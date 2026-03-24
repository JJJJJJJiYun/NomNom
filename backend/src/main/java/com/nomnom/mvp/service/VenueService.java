package com.nomnom.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nomnom.mvp.domain.Venue;
import com.nomnom.mvp.domain.VenueSearchQuery;
import com.nomnom.mvp.support.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class VenueService {
    private static final Logger logger = LoggerFactory.getLogger(VenueService.class);
    private static final String SEARCH_STRATEGY_REMOTE_AMAP = "REMOTE_AMAP";
    private static final String SEARCH_STRATEGY_REMOTE_TENCENT = "REMOTE_TENCENT";
    private static final String SEARCH_STRATEGY_LOCAL_CACHE = "LOCAL_CACHE";
    private static final String SOURCE_PROVIDER_AMAP = "AMAP_LBS";
    private static final String SOURCE_PROVIDER_TENCENT = "TENCENT_LBS";
    private static final String SOURCE_PROVIDER_BAIDU = "BAIDU_MAPS";
    private static final String SOURCE_PROVIDER_LOCAL_SAMPLE = "LOCAL_SAMPLE";
    private static final String AMAP_PLACE_AROUND_PATH = "/v3/place/around";
    private static final String AMAP_DISTRICT_PATH = "/v3/config/district";
    private static final String BAIDU_PLACE_SEARCH_PATH = "/place/v2/search";
    private static final String TENCENT_PLACE_SEARCH_PATH = "/ws/place/v1/search";
    private static final Duration TENCENT_QUOTA_BACKOFF = Duration.ofMinutes(5);
    private static final int BAIDU_ENRICH_LIMIT = 5;
    private static final int BAIDU_ENRICH_RADIUS_METERS = 800;

    private static final Map<String, String> CITY_ALIASES = Map.of(
            "shanghai", "上海",
            "beijing", "北京",
            "guangzhou", "广州",
            "shenzhen", "深圳"
    );
    private static final Map<String, String> CITY_AMAP_CODES = Map.of(
            "shanghai", "310000",
            "beijing", "110000",
            "guangzhou", "440100",
            "shenzhen", "440300"
    );

    private final Map<UUID, Venue> venues = new ConcurrentHashMap<>();
    private final List<Venue> fallbackVenues;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String poiProvider;
    private final String amapApiKey;
    private final String amapBaseUrl;
    private final String tencentApiKey;
    private final String tencentSecretKey;
    private final String tencentBaseUrl;
    private final String baiduApiKey;
    private final String baiduBaseUrl;
    private final boolean fallbackEnabled;
    private final AtomicLong tencentQuotaBackoffUntilEpochMillis = new AtomicLong(0);
    private final Map<String, BaiduVenueEnhancement> baiduEnhancementCache = new ConcurrentHashMap<>();

    public VenueService() {
        this(new ObjectMapper(), "amap", "", "https://restapi.amap.com", "", "", "https://apis.map.qq.com", "", "https://api.map.baidu.com", true, true);
    }

    @Autowired
    public VenueService(
            ObjectMapper objectMapper,
            @Value("${nomnom.poi.provider:tencent}") String poiProvider,
            @Value("${nomnom.poi.amap.api-key:}") String amapApiKey,
            @Value("${nomnom.poi.amap.base-url:https://restapi.amap.com}") String amapBaseUrl,
            @Value("${nomnom.poi.tencent.api-key:}") String tencentApiKey,
            @Value("${nomnom.poi.tencent.secret-key:}") String tencentSecretKey,
            @Value("${nomnom.poi.tencent.base-url:https://apis.map.qq.com}") String tencentBaseUrl,
            @Value("${nomnom.poi.baidu.api-key:}") String baiduApiKey,
            @Value("${nomnom.poi.baidu.base-url:https://api.map.baidu.com}") String baiduBaseUrl,
            @Value("${nomnom.poi.fallback-enabled:true}") boolean fallbackEnabled
    ) {
        this(objectMapper, poiProvider, amapApiKey, amapBaseUrl, tencentApiKey, tencentSecretKey, tencentBaseUrl, baiduApiKey, baiduBaseUrl, fallbackEnabled, true);
    }

    public VenueService(
            ObjectMapper objectMapper,
            String poiProvider,
            String tencentApiKey,
            String tencentSecretKey,
            String tencentBaseUrl,
            boolean fallbackEnabled
    ) {
        this(objectMapper, poiProvider, "", "https://restapi.amap.com", tencentApiKey, tencentSecretKey, tencentBaseUrl, "", "https://api.map.baidu.com", fallbackEnabled, true);
    }

    public VenueService(
            ObjectMapper objectMapper,
            String poiProvider,
            String tencentApiKey,
            String tencentBaseUrl,
            boolean fallbackEnabled
    ) {
        this(objectMapper, poiProvider, "", "https://restapi.amap.com", tencentApiKey, "", tencentBaseUrl, "", "https://api.map.baidu.com", fallbackEnabled, true);
    }

    VenueService(
            ObjectMapper objectMapper,
            String poiProvider,
            String amapApiKey,
            String amapBaseUrl,
            String tencentApiKey,
            String tencentSecretKey,
            String tencentBaseUrl,
            String baiduApiKey,
            String baiduBaseUrl,
            boolean fallbackEnabled,
            boolean ignored
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.poiProvider = normalizeProvider(poiProvider);
        this.amapApiKey = amapApiKey == null ? "" : amapApiKey.trim();
        this.amapBaseUrl = normalizeBaseUrl(amapBaseUrl, "https://restapi.amap.com");
        this.tencentApiKey = tencentApiKey == null ? "" : tencentApiKey.trim();
        this.tencentSecretKey = tencentSecretKey == null ? "" : tencentSecretKey.trim();
        this.tencentBaseUrl = normalizeBaseUrl(tencentBaseUrl, "https://apis.map.qq.com");
        this.baiduApiKey = baiduApiKey == null ? "" : baiduApiKey.trim();
        this.baiduBaseUrl = normalizeBaseUrl(baiduBaseUrl, "https://api.map.baidu.com");
        this.fallbackEnabled = fallbackEnabled;
        this.fallbackVenues = List.copyOf(seedSampleData());
        this.fallbackVenues.forEach(venue -> venues.put(venue.id(), venue));
    }

    public SearchResult search(VenueSearchQuery query) {
        Comparator<Venue> sort = comparatorFor(query);

        RemotePoiSearchResult remoteResult = searchRemotePoi(query, sort);
        if (!remoteResult.fallbackToLocal()) {
            return new SearchResult(remoteResult.items(), remoteResult.searchStrategy(), remoteResult.notice());
        }

        List<Venue> localMatches = venues.values().stream()
                .filter(venue -> matches(venue, query))
                .sorted(sort)
                .toList();
        return new SearchResult(localMatches, SEARCH_STRATEGY_LOCAL_CACHE, remoteResult.notice());
    }

    public FilterMetadata filterMetadata(String cityCode) {
        return filterMetadata(new VenueSearchQuery(cityCode, 31.2304, 121.4737, 3000, null, null, null, null, null, null, null, null, 1, 20));
    }

    public FilterMetadata filterMetadata(VenueSearchQuery query) {
        FilterMetadata remoteMetadata = filterMetadataFromConfiguredProvider(query);
        if (remoteMetadata != null) {
            return remoteMetadata;
        }

        String cityCode = query == null ? null : query.cityCode();
        String normalizedCityCode = cityCode == null || cityCode.isBlank()
                ? "shanghai"
                : cityCode.trim().toLowerCase(Locale.ROOT);

        List<Venue> cityVenues = fallbackVenues.stream()
                .filter(venue -> normalizedCityCode.equalsIgnoreCase(venue.cityCode()))
                .toList();

        Map<String, TreeSet<String>> businessAreasByDistrict = new LinkedHashMap<>();
        TreeSet<String> categoryOptions = new TreeSet<>();
        int minPrice = Integer.MAX_VALUE;
        int maxPrice = Integer.MIN_VALUE;

        cityVenues.stream()
                .sorted(Comparator.comparing(Venue::district).thenComparing(Venue::businessArea).thenComparing(Venue::name))
                .forEach(venue -> {
                    businessAreasByDistrict
                            .computeIfAbsent(venue.district(), ignored -> new TreeSet<>())
                            .add(venue.businessArea());
                    categoryOptions.add(venue.category());
                    categoryOptions.add(venue.subcategory());
                });

        for (Venue venue : cityVenues) {
            minPrice = Math.min(minPrice, venue.avgPrice());
            maxPrice = Math.max(maxPrice, venue.avgPrice());
        }

        int safeMinPrice = minPrice == Integer.MAX_VALUE ? 0 : roundDown(minPrice, 10);
        int safeMaxPrice = maxPrice == Integer.MIN_VALUE ? 500 : roundUp(maxPrice, 10);

        List<DistrictFilter> districts = businessAreasByDistrict.entrySet().stream()
                .map(entry -> new DistrictFilter(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();

        return new FilterMetadata(
                normalizedCityCode,
                districts,
                List.copyOf(categoryOptions),
                List.of(
                        new SortOption("RECOMMENDED", "推荐排序"),
                        new SortOption("DISTANCE", "距离最近"),
                        new SortOption("RATING", "评分最高"),
                        new SortOption("PRICE_ASC", "人均从低到高"),
                        new SortOption("PRICE_DESC", "人均从高到低")
                ),
                new NumericRange(safeMinPrice, safeMaxPrice, 10),
                List.of(3.5, 4.0, 4.5),
                List.of(1000, 2000, 3000, 5000, 8000),
                List.of(1, 2, 4, 6, 8)
        );
    }

    public Venue get(UUID venueId) {
        Venue venue = venues.get(venueId);
        if (venue == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "VENUE_NOT_FOUND", "Venue not found");
        }
        return venue;
    }

    public List<Venue> getAll(List<UUID> venueIds) {
        return venueIds.stream().distinct().map(this::get).toList();
    }

    public Venue createManualVenue(
            String name,
            String category,
            int avgPrice,
            String district,
            String businessArea,
            String address,
            List<String> tags,
            String sourceProvider,
            String sourceUrl
    ) {
        Venue venue = new Venue(
                UUID.randomUUID(),
                name,
                normalize(name),
                "shanghai",
                district,
                businessArea,
                address,
                31.2304,
                121.4737,
                category,
                category,
                avgPrice,
                4.3,
                0,
                Venue.OpenStatus.OPEN,
                null,
                sourceProvider,
                sourceUrl,
                tags == null ? List.of() : List.copyOf(tags)
        );
        venues.put(venue.id(), venue);
        return venue;
    }

    public Integer distanceMeters(Double latitude, Double longitude, Venue venue) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return distanceMeters(latitude, longitude, venue.latitude(), venue.longitude());
    }

    private Integer distanceMeters(Double latitude, Double longitude, double venueLatitude, double venueLongitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        double earthRadius = 6_371_000d;
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(venueLatitude);
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(venueLongitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(earthRadius * c);
    }

    private RemotePoiSearchResult searchRemotePoi(VenueSearchQuery query, Comparator<Venue> sort) {
        List<String> notices = new ArrayList<>();
        for (String provider : remoteSearchProviders()) {
            RemotePoiSearchResult result = searchRemotePoi(query, sort, provider);
            if (hasText(result.notice())) {
                notices.add(result.notice());
            }
            if (!result.fallbackToLocal()) {
                return new RemotePoiSearchResult(
                        result.items(),
                        result.searchStrategy(),
                        mergeNotices(notices),
                        false
                );
            }
        }
        return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_LOCAL_CACHE, mergeNotices(notices), true);
    }

    private List<String> remoteSearchProviders() {
        LinkedHashSet<String> providers = new LinkedHashSet<>();
        providers.add(poiProvider);
        if (fallbackEnabled) {
            if (!"amap".equals(poiProvider)) {
                providers.add("amap");
            }
            if (!"tencent".equals(poiProvider)) {
                providers.add("tencent");
            }
        }
        return providers.stream().toList();
    }

    private RemotePoiSearchResult searchRemotePoi(VenueSearchQuery query, Comparator<Venue> sort, String provider) {
        return switch (provider) {
            case "amap" -> searchAmapRemotePoi(query, sort);
            case "tencent" -> searchTencentRemotePoi(query, sort);
            default -> new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_LOCAL_CACHE, "未识别的 POI provider，当前已回退到本地缓存数据。", true);
        };
    }

    private FilterMetadata filterMetadataFromConfiguredProvider(VenueSearchQuery query) {
        LinkedHashSet<String> providers = new LinkedHashSet<>();
        providers.add(poiProvider);
        if (fallbackEnabled && !"amap".equals(poiProvider)) {
            providers.add("amap");
        }
        for (String provider : providers) {
            if (!"amap".equals(provider)) {
                continue;
            }
            try {
                FilterMetadata metadata = filterMetadataFromAmap(query);
                if (metadata != null) {
                    return metadata;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Amap filter metadata fetch interrupted, fallback to local metadata");
                return null;
            } catch (IOException | RuntimeException e) {
                logger.warn("Amap filter metadata fetch failed, fallback to local metadata", e);
                if (!fallbackEnabled) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "POI_PROVIDER_UNAVAILABLE", "Amap filter metadata fetch failed");
                }
            }
        }
        return null;
    }

    private RemotePoiSearchResult searchTencentRemotePoi(VenueSearchQuery query, Comparator<Venue> sort) {
        if (tencentApiKey.isBlank()) {
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_TENCENT, "未配置 TENCENT_MAPS_API_KEY。", true);
        }
        long backoffUntil = tencentQuotaBackoffUntilEpochMillis.get();
        if (backoffUntil > System.currentTimeMillis()) {
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_TENCENT, "腾讯位置服务额度刚刚触发限流，当前暂时切回本地缓存和样例数据。稍后会自动重试。", true);
        }
        if (query.latitude() == null || query.longitude() == null) {
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_TENCENT, "缺少定位坐标，当前显示本地缓存数据。", true);
        }

        try {
            List<Venue> venues = searchTencentPoi(query);
            tencentQuotaBackoffUntilEpochMillis.set(0);
            return new RemotePoiSearchResult(
                    venues.stream()
                            .filter(venue -> matches(venue, query))
                            .sorted(sort)
                            .toList(),
                    SEARCH_STRATEGY_REMOTE_TENCENT,
                    null,
                    false
            );
        } catch (ApiException e) {
            if (!fallbackEnabled) {
                throw e;
            }
            String notice = noticeForTencentApiException(e);
            logger.warn("Tencent POI search fallback: {}", e.getMessage());
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_TENCENT, notice, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!fallbackEnabled) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "POI_PROVIDER_UNAVAILABLE", "Tencent Maps POI search failed");
            }
            logger.warn("Tencent POI search failed, fallback to sample data", e);
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_TENCENT, "腾讯位置服务请求被中断，已回退到本地缓存数据。", true);
        } catch (IOException e) {
            if (!fallbackEnabled) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "POI_PROVIDER_UNAVAILABLE", "Tencent Maps POI search failed");
            }
            logger.warn("Tencent POI search failed, fallback to sample data", e);
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_TENCENT, "腾讯位置服务暂时不可用，已回退到本地缓存数据。", true);
        } catch (RuntimeException e) {
            if (!fallbackEnabled) {
                throw e;
            }
            logger.warn("Tencent POI search returned unexpected payload, fallback to sample data", e);
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_TENCENT, "腾讯位置服务返回异常数据，已回退到本地缓存数据。", true);
        }
    }

    private RemotePoiSearchResult searchAmapRemotePoi(VenueSearchQuery query, Comparator<Venue> sort) {
        if (amapApiKey.isBlank()) {
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_AMAP, "未配置 AMAP_WEB_SERVICE_KEY。", true);
        }
        if (query.latitude() == null || query.longitude() == null) {
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_AMAP, "缺少定位坐标，当前显示本地缓存数据。", true);
        }

        try {
            List<Venue> venues = enrichAmapResultsWithBaidu(query, searchAmapPoi(query));
            return new RemotePoiSearchResult(
                    venues.stream()
                            .filter(venue -> matches(venue, query))
                            .sorted(sort)
                            .toList(),
                    SEARCH_STRATEGY_REMOTE_AMAP,
                    null,
                    false
            );
        } catch (ApiException e) {
            if (!fallbackEnabled) {
                throw e;
            }
            logger.warn("Amap POI search fallback: {}", e.getMessage());
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_AMAP, noticeForAmapApiException(e), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!fallbackEnabled) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "POI_PROVIDER_UNAVAILABLE", "Amap POI search failed");
            }
            logger.warn("Amap POI search failed, fallback to sample data", e);
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_AMAP, "高德位置服务请求被中断，已回退到本地缓存数据。", true);
        } catch (IOException e) {
            if (!fallbackEnabled) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "POI_PROVIDER_UNAVAILABLE", "Amap POI search failed");
            }
            logger.warn("Amap POI search failed, fallback to sample data", e);
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_AMAP, "高德位置服务暂时不可用，已回退到本地缓存数据。", true);
        } catch (RuntimeException e) {
            if (!fallbackEnabled) {
                throw e;
            }
            logger.warn("Amap POI search returned unexpected payload, fallback to sample data", e);
            return new RemotePoiSearchResult(List.of(), SEARCH_STRATEGY_REMOTE_AMAP, "高德位置服务返回异常数据，已回退到本地缓存数据。", true);
        }
    }

    private String noticeForTencentApiException(ApiException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("每日调用量已达到上限")) {
            tencentQuotaBackoffUntilEpochMillis.set(System.currentTimeMillis() + TENCENT_QUOTA_BACKOFF.toMillis());
            return "腾讯位置服务当日额度已用尽，当前已切回本地缓存和样例数据。请明天重试，或更换有剩余额度的 Key。";
        }
        if (message.contains("签名验证失败")) {
            return "腾讯位置服务签名校验失败，当前已切回本地缓存和样例数据。请检查 Key / SK 配置。";
        }
        if (message.contains("请求来源未被授权") || message.contains("IP未被授权")) {
            return "腾讯位置服务未授权当前请求来源，当前已切回本地缓存和样例数据。请检查控制台白名单配置。";
        }
        return "腾讯位置服务暂时不可用，当前已切回本地缓存和样例数据。";
    }

    private String noticeForAmapApiException(ApiException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("DAILY_QUERY_OVER_LIMIT") || message.contains("USER_DAILY_QUERY_OVER_LIMIT")) {
            return "高德位置服务当日额度已用尽，当前已切回其他数据源或本地缓存。";
        }
        if (message.contains("INVALID_USER_KEY") || message.contains("USERKEY_PLAT_NOMATCH")) {
            return "高德 Web 服务 Key 无效或未授权，当前已切回其他数据源或本地缓存。";
        }
        if (message.contains("ACCESS_TOO_FREQUENT")) {
            return "高德位置服务请求过于频繁，当前已切回其他数据源或本地缓存。";
        }
        return "高德位置服务暂时不可用，当前已切回其他数据源或本地缓存。";
    }

    private FilterMetadata filterMetadataFromAmap(VenueSearchQuery query) throws IOException, InterruptedException {
        if (amapApiKey.isBlank()) {
            return null;
        }

        String normalizedCityCode = query == null || query.cityCode() == null || query.cityCode().isBlank()
                ? "shanghai"
                : query.cityCode().trim().toLowerCase(Locale.ROOT);
        VenueSearchQuery facetQuery = new VenueSearchQuery(
                normalizedCityCode,
                query != null && query.latitude() != null ? query.latitude() : 31.2304,
                query != null && query.longitude() != null ? query.longitude() : 121.4737,
                query != null && query.radiusMeters() != null ? Math.max(1000, Math.min(query.radiusMeters(), 5000)) : 3000,
                query == null ? null : query.district(),
                query == null ? null : query.businessArea(),
                null,
                null,
                null,
                null,
                null,
                "DISTANCE",
                1,
                25
        );

        List<Venue> facetVenues = searchAmapPoi(facetQuery);
        LinkedHashMap<String, TreeSet<String>> businessAreasByDistrict = new LinkedHashMap<>();
        for (String districtName : fetchAmapDistrictNames(normalizedCityCode)) {
            businessAreasByDistrict.putIfAbsent(districtName, new TreeSet<>());
        }

        TreeSet<String> categoryOptions = new TreeSet<>();
        int minPrice = Integer.MAX_VALUE;
        int maxPrice = Integer.MIN_VALUE;

        for (Venue venue : facetVenues) {
            if (hasText(venue.district())) {
                businessAreasByDistrict.computeIfAbsent(venue.district(), ignored -> new TreeSet<>());
                if (hasText(venue.businessArea())) {
                    businessAreasByDistrict.get(venue.district()).add(venue.businessArea());
                }
            }
            if (hasText(venue.category())) {
                categoryOptions.add(venue.category());
            }
            if (hasText(venue.subcategory())) {
                categoryOptions.add(venue.subcategory());
            }
            if (venue.avgPrice() > 0) {
                minPrice = Math.min(minPrice, venue.avgPrice());
                maxPrice = Math.max(maxPrice, venue.avgPrice());
            }
        }

        if (query != null && hasText(query.district())) {
            businessAreasByDistrict.computeIfAbsent(query.district().trim(), ignored -> new TreeSet<>());
            if (hasText(query.businessArea())) {
                businessAreasByDistrict.get(query.district().trim()).add(query.businessArea().trim());
            }
        }

        if (businessAreasByDistrict.isEmpty() || categoryOptions.isEmpty()) {
            return null;
        }

        int safeMinPrice = minPrice == Integer.MAX_VALUE ? 0 : roundDown(minPrice, 10);
        int safeMaxPrice = maxPrice == Integer.MIN_VALUE ? 500 : roundUp(maxPrice, 10);
        List<DistrictFilter> districts = businessAreasByDistrict.entrySet().stream()
                .map(entry -> new DistrictFilter(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();

        return new FilterMetadata(
                normalizedCityCode,
                districts,
                List.copyOf(categoryOptions),
                List.of(
                        new SortOption("RECOMMENDED", "推荐排序"),
                        new SortOption("DISTANCE", "距离最近"),
                        new SortOption("RATING", "评分最高"),
                        new SortOption("PRICE_ASC", "人均从低到高"),
                        new SortOption("PRICE_DESC", "人均从高到低")
                ),
                new NumericRange(safeMinPrice, safeMaxPrice, 10),
                List.of(3.5, 4.0, 4.5),
                List.of(1000, 2000, 3000, 5000, 8000),
                List.of(1, 2, 4, 6, 8)
        );
    }

    private List<String> fetchAmapDistrictNames(String cityCode) throws IOException, InterruptedException {
        HttpRequest request = buildAmapDistrictRequest(cityCode);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "POI_PROVIDER_UNAVAILABLE", "Amap district fetch failed");
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!"1".equals(root.path("status").asText())) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "POI_PROVIDER_UNAVAILABLE",
                    "Amap district fetch failed: " + root.path("info").asText("unknown error")
            );
        }

        LinkedHashSet<String> districtNames = new LinkedHashSet<>();
        collectAmapDistrictNames(root.path("districts"), districtNames);
        return districtNames.stream().filter(this::hasText).toList();
    }

    private void collectAmapDistrictNames(JsonNode nodes, LinkedHashSet<String> districtNames) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            if ("district".equalsIgnoreCase(node.path("level").asText())) {
                districtNames.add(node.path("name").asText(""));
            }
            collectAmapDistrictNames(node.path("districts"), districtNames);
        }
    }

    private List<Venue> searchAmapPoi(VenueSearchQuery query) throws IOException, InterruptedException {
        LinkedHashMap<String, Venue> resultsBySourceId = new LinkedHashMap<>();

        for (String keyword : buildAmapKeywords(query)) {
            HttpRequest request = buildAmapSearchRequest(query, keyword);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "POI_PROVIDER_UNAVAILABLE", "Amap POI search failed");
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!"1".equals(root.path("status").asText())) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "POI_PROVIDER_UNAVAILABLE",
                        "Amap POI search failed: " + root.path("info").asText("unknown error")
                );
            }

            for (JsonNode item : root.path("pois")) {
                Venue venue = toAmapVenue(item, query, keyword);
                String sourceId = item.path("id").asText(venue.id().toString());
                resultsBySourceId.putIfAbsent(sourceId, venue);
                venues.put(venue.id(), venue);
                if (resultsBySourceId.size() >= query.safePageSize()) {
                    return List.copyOf(resultsBySourceId.values());
                }
            }
        }

        return List.copyOf(resultsBySourceId.values());
    }

    private List<Venue> enrichAmapResultsWithBaidu(VenueSearchQuery query, List<Venue> sourceVenues) {
        if (sourceVenues.isEmpty() || baiduApiKey.isBlank()) {
            return sourceVenues;
        }

        List<Venue> enrichedVenues = new ArrayList<>(sourceVenues.size());
        int enrichLimit = Math.min(BAIDU_ENRICH_LIMIT, sourceVenues.size());

        for (int index = 0; index < sourceVenues.size(); index++) {
            Venue venue = sourceVenues.get(index);
            if (index >= enrichLimit) {
                enrichedVenues.add(venue);
                continue;
            }

            try {
                Venue enrichedVenue = enrichVenueWithBaidu(query, venue);
                venues.put(enrichedVenue.id(), enrichedVenue);
                enrichedVenues.add(enrichedVenue);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Baidu detail enrichment interrupted, keeping Amap result for {}", venue.name());
                enrichedVenues.add(venue);
            } catch (IOException | RuntimeException e) {
                logger.warn("Baidu detail enrichment skipped for {}: {}", venue.name(), e.getMessage());
                enrichedVenues.add(venue);
            }
        }

        return List.copyOf(enrichedVenues);
    }

    private Venue enrichVenueWithBaidu(VenueSearchQuery query, Venue venue) throws IOException, InterruptedException {
        String cacheKey = buildBaiduCacheKey(venue);
        BaiduVenueEnhancement cachedEnhancement = baiduEnhancementCache.get(cacheKey);
        if (cachedEnhancement != null) {
            return applyBaiduEnhancement(venue, cachedEnhancement);
        }

        HttpRequest request = buildBaiduSearchRequest(query, venue);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Baidu detail enrichment failed with status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (root.path("status").asInt(-1) != 0) {
            String message = firstNonBlank(root.path("message").asText(null), root.path("msg").asText(null), "unknown error");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "POI_PROVIDER_UNAVAILABLE", "Baidu detail enrichment failed: " + message);
        }

        BaiduVenueEnhancement enhancement = selectBaiduEnhancement(root.path("results"), venue);
        if (enhancement == null) {
            return venue;
        }

        baiduEnhancementCache.put(cacheKey, enhancement);
        return applyBaiduEnhancement(venue, enhancement);
    }

    private HttpRequest buildBaiduSearchRequest(VenueSearchQuery query, Venue venue) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ak", baiduApiKey);
        params.put("output", "json");
        params.put("scope", "2");
        params.put("query", venue.name());
        params.put("location", venue.latitude() + "," + venue.longitude());
        params.put("radius", String.valueOf(Math.min(query.radiusMeters() == null ? BAIDU_ENRICH_RADIUS_METERS : query.radiusMeters(), BAIDU_ENRICH_RADIUS_METERS)));
        params.put("page_size", "10");
        params.put("page_num", "0");
        params.put("tag", "美食");
        params.put("region", cityAlias(query.cityCode()));
        return HttpRequest.newBuilder(buildBaiduUri(BAIDU_PLACE_SEARCH_PATH, params))
                .GET()
                .timeout(Duration.ofSeconds(6))
                .build();
    }

    private URI buildBaiduUri(String path, Map<String, String> params) {
        String queryString = params.entrySet().stream()
                .filter(entry -> hasText(entry.getValue()))
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return URI.create(baiduBaseUrl + path + "?" + queryString);
    }

    private BaiduVenueEnhancement selectBaiduEnhancement(JsonNode results, Venue venue) {
        if (results == null || !results.isArray()) {
            return null;
        }

        BaiduVenueEnhancement bestEnhancement = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (JsonNode result : results) {
            double score = baiduMatchScore(result, venue);
            if (score <= bestScore) {
                continue;
            }

            BaiduVenueEnhancement enhancement = toBaiduEnhancement(result);
            if (enhancement == null) {
                continue;
            }

            bestScore = score;
            bestEnhancement = enhancement;
        }

        return bestScore >= 2.4 ? bestEnhancement : null;
    }

    private double baiduMatchScore(JsonNode result, Venue venue) {
        String resultName = result.path("name").asText("");
        if (!hasText(resultName)) {
            return Double.NEGATIVE_INFINITY;
        }

        String normalizedResultName = normalize(resultName);
        double score = 0;
        if (normalizedResultName.equals(venue.normalizedName())) {
            score += 6.0;
        } else if (normalizedResultName.contains(venue.normalizedName()) || venue.normalizedName().contains(normalizedResultName)) {
            score += 3.5;
        }

        String tag = firstNonBlank(result.path("detail_info").path("tag").asText(null), result.path("tag").asText(null));
        if (hasText(tag) && (tag.contains("美食") || tag.contains(venue.category()) || tag.contains(venue.subcategory()))) {
            score += 0.8;
        }

        String address = result.path("address").asText("");
        if (hasText(venue.district()) && address.contains(venue.district())) {
            score += 0.6;
        }
        if (hasText(venue.businessArea()) && address.contains(venue.businessArea())) {
            score += 0.4;
        }

        Integer distanceMeters = baiduDistanceMeters(result, venue);
        if (distanceMeters != null) {
            if (distanceMeters <= 150) {
                score += 2.0;
            } else if (distanceMeters <= 400) {
                score += 1.2;
            } else if (distanceMeters <= 800) {
                score += 0.5;
            } else {
                score -= 1.0;
            }
        }

        if (hasMeaningfulBaiduDetails(result)) {
            score += 0.4;
        }
        return score;
    }

    private Integer baiduDistanceMeters(JsonNode result, Venue venue) {
        JsonNode location = result.path("location");
        if (!location.isObject()) {
            return null;
        }
        if (!location.path("lat").isNumber() || !location.path("lng").isNumber()) {
            return null;
        }
        return distanceMeters(venue.latitude(), venue.longitude(), location.path("lat").asDouble(), location.path("lng").asDouble());
    }

    private boolean hasMeaningfulBaiduDetails(JsonNode result) {
        JsonNode detailInfo = result.path("detail_info");
        return hasText(detailInfo.path("overall_rating").asText(null))
                || hasText(detailInfo.path("price").asText(null))
                || hasText(detailInfo.path("comment_num").asText(null))
                || hasText(detailInfo.path("detail_url").asText(null));
    }

    private BaiduVenueEnhancement toBaiduEnhancement(JsonNode result) {
        JsonNode detailInfo = result.path("detail_info");
        Integer avgPrice = parsePositiveInt(detailInfo.path("price").asText(null)).orElse(null);
        Double rating = parsePositiveDouble(detailInfo.path("overall_rating").asText(null)).orElse(null);
        Integer reviewCount = parsePositiveInt(detailInfo.path("comment_num").asText(null)).orElse(null);
        Venue.OpenStatus openStatus = parseBaiduOpenStatus(detailInfo);
        String sourceUrl = firstNonBlank(detailInfo.path("detail_url").asText(null), result.path("detail_url").asText(null));

        if (avgPrice == null && rating == null && reviewCount == null && openStatus == null && !hasText(sourceUrl)) {
            return null;
        }

        return new BaiduVenueEnhancement(avgPrice, rating, reviewCount, openStatus, sourceUrl);
    }

    private Venue.OpenStatus parseBaiduOpenStatus(JsonNode detailInfo) {
        if (detailInfo == null || detailInfo.isMissingNode()) {
            return null;
        }
        JsonNode statusNode = detailInfo.path("status");
        if (statusNode.isInt() || statusNode.isLong()) {
            int value = statusNode.asInt();
            if (value == 1) {
                return Venue.OpenStatus.OPEN;
            }
            if (value == 0) {
                return Venue.OpenStatus.CLOSED;
            }
        }
        String statusText = statusNode.asText(null);
        if (!hasText(statusText)) {
            return null;
        }
        String normalized = statusText.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("open") || normalized.contains("营业")) {
            return Venue.OpenStatus.OPEN;
        }
        if (normalized.contains("close") || normalized.contains("休息") || normalized.contains("停业")) {
            return Venue.OpenStatus.CLOSED;
        }
        return null;
    }

    private String buildBaiduCacheKey(Venue venue) {
        return normalize(venue.name()) + ":" + Math.round(venue.latitude() * 10_000) + ":" + Math.round(venue.longitude() * 10_000);
    }

    private Venue applyBaiduEnhancement(Venue venue, BaiduVenueEnhancement enhancement) {
        return new Venue(
                venue.id(),
                venue.name(),
                venue.normalizedName(),
                venue.cityCode(),
                venue.district(),
                venue.businessArea(),
                venue.address(),
                venue.latitude(),
                venue.longitude(),
                venue.category(),
                venue.subcategory(),
                enhancement.avgPrice() == null ? venue.avgPrice() : enhancement.avgPrice(),
                enhancement.rating() == null ? venue.rating() : enhancement.rating(),
                enhancement.reviewCount() == null ? venue.reviewCount() : enhancement.reviewCount(),
                enhancement.openStatus() == null ? venue.openStatus() : enhancement.openStatus(),
                venue.coverImageUrl(),
                venue.sourceProvider(),
                hasText(venue.sourceUrl()) ? venue.sourceUrl() : enhancement.sourceUrl(),
                venue.tags()
        );
    }

    private HttpRequest buildAmapSearchRequest(VenueSearchQuery query, String keyword) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", amapApiKey);
        params.put("location", formatLocation(query.longitude(), query.latitude(), 121.4737, 31.2304));
        params.put("radius", String.valueOf(Math.max(1, Math.min(query.radiusMeters() == null ? 3000 : query.radiusMeters(), 50_000))));
        params.put("sortrule", "distance");
        params.put("offset", String.valueOf(Math.min(query.safePageSize(), 25)));
        params.put("page", "1");
        params.put("extensions", "all");
        params.put("output", "JSON");
        params.put("types", "050000");
        params.put("city", cityAmapValue(query.cityCode()));
        if (hasText(keyword)) {
            params.put("keywords", keyword);
        }
        return HttpRequest.newBuilder(buildAmapUri(AMAP_PLACE_AROUND_PATH, params))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();
    }

    private HttpRequest buildAmapDistrictRequest(String cityCode) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", amapApiKey);
        params.put("keywords", cityAlias(cityCode));
        params.put("subdistrict", "1");
        params.put("extensions", "base");
        params.put("output", "JSON");
        return HttpRequest.newBuilder(buildAmapUri(AMAP_DISTRICT_PATH, params))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();
    }

    private URI buildAmapUri(String path, Map<String, String> params) {
        String queryString = params.entrySet().stream()
                .filter(entry -> hasText(entry.getValue()))
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return URI.create(amapBaseUrl + path + "?" + queryString);
    }

    private List<String> buildAmapKeywords(VenueSearchQuery query) {
        List<String> areaHints = new ArrayList<>();
        if (hasText(query.businessArea())) {
            areaHints.add(query.businessArea().trim());
        }
        if (hasText(query.district())) {
            areaHints.add(query.district().trim());
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        List<String> categories = query.categories() == null ? List.of() : query.categories().stream()
                .filter(this::hasText)
                .map(this::searchKeywordForCategory)
                .distinct()
                .limit(3)
                .toList();

        if (categories.isEmpty()) {
            keywords.add(joinKeyword(areaHints, "餐厅"));
        } else {
            for (String category : categories) {
                keywords.add(joinKeyword(areaHints, category));
            }
            keywords.add(joinKeyword(areaHints, "餐厅"));
        }

        return keywords.stream()
                .map(keyword -> keyword.isBlank() ? "餐厅" : keyword)
                .toList();
    }

    private Venue toAmapVenue(JsonNode item, VenueSearchQuery query, String keyword) {
        String sourceId = item.path("id").asText(UUID.randomUUID().toString());
        String title = item.path("name").asText("未知地点");
        String address = item.path("address").asText("");
        double[] location = parseLocation(item.path("location").asText(null), query);
        String district = firstNonBlank(
                item.path("adname").asText(null),
                query.district(),
                cityAlias(query.cityCode())
        );
        String businessArea = firstNonBlank(
                item.path("business_area").asText(null),
                inferBusinessArea(query, title, address, district)
        );
        String rawCategory = item.path("type").asText("");
        CategoryParts categoryParts = categoryParts(rawCategory, query.categories(), keyword);
        Integer distanceMeters = firstDistanceHint(item, query, location[1], location[0]);
        int avgPrice = parsePositiveInt(item.path("biz_ext").path("cost").asText(null))
                .orElseGet(() -> estimateAveragePrice(categoryParts.category(), categoryParts.subcategory()));
        double rating = parsePositiveDouble(item.path("biz_ext").path("rating").asText(null))
                .orElseGet(() -> estimateRating(categoryParts.category(), distanceMeters));
        int reviewCount = estimateReviewCount(distanceMeters);
        String coverImageUrl = item.path("photos").isArray() && !item.path("photos").isEmpty()
                ? item.path("photos").get(0).path("url").asText(null)
                : null;

        return new Venue(
                UUID.nameUUIDFromBytes((SOURCE_PROVIDER_AMAP + ":" + sourceId).getBytes(StandardCharsets.UTF_8)),
                title,
                normalize(title),
                query.cityCode() == null || query.cityCode().isBlank() ? "shanghai" : query.cityCode().trim().toLowerCase(Locale.ROOT),
                district,
                businessArea,
                address,
                location[1],
                location[0],
                categoryParts.category(),
                categoryParts.subcategory(),
                avgPrice,
                rating,
                reviewCount,
                Venue.OpenStatus.OPEN,
                coverImageUrl,
                SOURCE_PROVIDER_AMAP,
                null,
                List.of(categoryParts.category(), categoryParts.subcategory())
        );
    }

    private double[] parseLocation(String rawLocation, VenueSearchQuery query) {
        double fallbackLng = query.longitude() == null ? 121.4737 : query.longitude();
        double fallbackLat = query.latitude() == null ? 31.2304 : query.latitude();
        if (!hasText(rawLocation) || !rawLocation.contains(",")) {
            return new double[]{fallbackLng, fallbackLat};
        }
        String[] parts = rawLocation.split(",", 2);
        try {
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (NumberFormatException ignored) {
            return new double[]{fallbackLng, fallbackLat};
        }
    }

    private String formatLocation(Double longitude, Double latitude, double fallbackLongitude, double fallbackLatitude) {
        double lng = longitude == null ? fallbackLongitude : longitude;
        double lat = latitude == null ? fallbackLatitude : latitude;
        return lng + "," + lat;
    }

    private String cityAmapValue(String cityCode) {
        if (cityCode == null || cityCode.isBlank()) {
            return CITY_AMAP_CODES.get("shanghai");
        }
        String normalized = cityCode.trim().toLowerCase(Locale.ROOT);
        return CITY_AMAP_CODES.getOrDefault(normalized, cityAlias(normalized));
    }

    private java.util.Optional<Integer> parsePositiveInt(String value) {
        if (!hasText(value)) {
            return java.util.Optional.empty();
        }
        try {
            int parsed = (int) Math.round(Double.parseDouble(value.trim()));
            return parsed > 0 ? java.util.Optional.of(parsed) : java.util.Optional.empty();
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<Double> parsePositiveDouble(String value) {
        if (!hasText(value)) {
            return java.util.Optional.empty();
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed > 0 ? java.util.Optional.of(parsed) : java.util.Optional.empty();
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private String mergeNotices(List<String> notices) {
        LinkedHashSet<String> uniqueNotices = new LinkedHashSet<>();
        for (String notice : notices) {
            if (hasText(notice)) {
                uniqueNotices.add(notice.trim());
            }
        }
        if (uniqueNotices.isEmpty()) {
            return null;
        }
        return String.join(" ", uniqueNotices);
    }

    private List<Venue> searchTencentPoi(VenueSearchQuery query) throws IOException, InterruptedException {
        LinkedHashMap<String, Venue> resultsBySourceId = new LinkedHashMap<>();

        for (String keyword : buildTencentKeywords(query)) {
            HttpRequest request = buildTencentSearchRequest(query, keyword);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "POI_PROVIDER_UNAVAILABLE", "Tencent Maps POI search failed");
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("status").asInt(-1) != 0) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "POI_PROVIDER_UNAVAILABLE",
                        "Tencent Maps POI search failed: " + root.path("message").asText("unknown error")
                );
            }

            for (JsonNode item : root.path("data")) {
                Venue venue = toTencentVenue(item, query, keyword);
                String sourceId = item.path("id").asText(venue.id().toString());
                resultsBySourceId.putIfAbsent(sourceId, venue);
                venues.put(venue.id(), venue);
                if (resultsBySourceId.size() >= query.safePageSize()) {
                    return List.copyOf(resultsBySourceId.values());
                }
            }
        }

        return List.copyOf(resultsBySourceId.values());
    }

    private HttpRequest buildTencentSearchRequest(VenueSearchQuery query, String keyword) {
        Map<String, String> params = buildTencentSearchParams(query, keyword);
        HttpRequest.Builder builder = HttpRequest.newBuilder(buildTencentSearchUri(params))
                .GET()
                .timeout(Duration.ofSeconds(8));
        if (requiresLegacyUrlDecode(params)) {
            builder.header("x-legacy-url-decode", "no");
        }
        return builder.build();
    }

    private Map<String, String> buildTencentSearchParams(VenueSearchQuery query, String keyword) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", tencentApiKey);
        params.put("keyword", keyword);
        params.put("boundary", buildTencentBoundary(query));
        params.put("orderby", "_distance");
        params.put("page_size", String.valueOf(Math.min(query.safePageSize(), 20)));
        params.put("page_index", "1");
        if (!tencentSecretKey.isBlank()) {
            params.put("sig", buildTencentSignature(TENCENT_PLACE_SEARCH_PATH, params));
        }
        return params;
    }

    private URI buildTencentSearchUri(Map<String, String> params) {
        String queryString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");

        return URI.create(tencentBaseUrl + TENCENT_PLACE_SEARCH_PATH + "?" + queryString);
    }

    private boolean requiresLegacyUrlDecode(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> !"sig".equals(entry.getKey()))
                .map(Map.Entry::getValue)
                .anyMatch(value -> value.contains("&") || value.contains("#"));
    }

    private String buildTencentSignature(String requestPath, Map<String, String> params) {
        String rawQuery = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        String payload = requestPath + "?" + rawQuery + tencentSecretKey;

        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }

    private String buildTencentBoundary(VenueSearchQuery query) {
        double latitude = query.latitude() == null ? 31.2304 : query.latitude();
        double longitude = query.longitude() == null ? 121.4737 : query.longitude();
        int radius = query.radiusMeters() == null ? 3000 : query.radiusMeters();
        return "nearby(" + latitude + "," + longitude + "," + radius + ",1)";
    }

    private List<String> buildTencentKeywords(VenueSearchQuery query) {
        List<String> areaHints = new ArrayList<>();
        if (hasText(query.businessArea())) {
            areaHints.add(query.businessArea().trim());
        }
        if (hasText(query.district())) {
            areaHints.add(query.district().trim());
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        List<String> categories = query.categories() == null ? List.of() : query.categories().stream()
                .filter(this::hasText)
                .map(this::searchKeywordForCategory)
                .distinct()
                .limit(3)
                .toList();

        if (categories.isEmpty()) {
            keywords.add(joinKeyword(areaHints, "餐厅"));
        } else {
            for (String category : categories) {
                keywords.add(joinKeyword(areaHints, category));
            }
            keywords.add(joinKeyword(areaHints, "餐厅"));
        }

        if (keywords.isEmpty()) {
            keywords.add(cityAlias(query.cityCode()) + " 美食");
        }

        return keywords.stream()
                .map(keyword -> keyword.isBlank() ? "美食" : keyword)
                .toList();
    }

    private Venue toTencentVenue(JsonNode item, VenueSearchQuery query, String keyword) {
        String sourceId = item.path("id").asText(UUID.randomUUID().toString());
        String title = item.path("title").asText("未知地点");
        String address = item.path("address").asText("");
        JsonNode location = item.path("location");
        double latitude = location.path("lat").asDouble(query.latitude() == null ? 31.2304 : query.latitude());
        double longitude = location.path("lng").asDouble(query.longitude() == null ? 121.4737 : query.longitude());
        JsonNode adInfo = item.path("ad_info");
        String district = firstNonBlank(
                adInfo.path("district").asText(null),
                query.district(),
                cityAlias(query.cityCode())
        );
        String businessArea = inferBusinessArea(query, title, address, district);
        String rawCategory = item.path("category").asText("");
        CategoryParts categoryParts = categoryParts(rawCategory, query.categories(), keyword);
        Integer distanceMeters = firstDistanceHint(item, query, latitude, longitude);
        int avgPrice = estimateAveragePrice(categoryParts.category(), categoryParts.subcategory());
        double rating = estimateRating(categoryParts.category(), distanceMeters);
        int reviewCount = estimateReviewCount(distanceMeters);

        return new Venue(
                UUID.nameUUIDFromBytes((SOURCE_PROVIDER_TENCENT + ":" + sourceId).getBytes(StandardCharsets.UTF_8)),
                title,
                normalize(title),
                query.cityCode() == null || query.cityCode().isBlank() ? "shanghai" : query.cityCode().trim().toLowerCase(Locale.ROOT),
                district,
                businessArea,
                address,
                latitude,
                longitude,
                categoryParts.category(),
                categoryParts.subcategory(),
                avgPrice,
                rating,
                reviewCount,
                Venue.OpenStatus.OPEN,
                null,
                SOURCE_PROVIDER_TENCENT,
                null,
                List.of(categoryParts.category(), categoryParts.subcategory())
        );
    }

    private Integer firstDistanceHint(JsonNode item, VenueSearchQuery query, double latitude, double longitude) {
        if (item.has("_distance") && item.path("_distance").canConvertToInt()) {
            return item.path("_distance").asInt();
        }
        if (item.has("distance")) {
            if (item.path("distance").canConvertToInt()) {
                return item.path("distance").asInt();
            }
            try {
                return (int) Math.round(Double.parseDouble(item.path("distance").asText("")));
            } catch (NumberFormatException ignored) {
                // Fall through to manual distance calculation.
            }
        }
        return distanceMeters(query.latitude(), query.longitude(), latitude, longitude);
    }

    private String inferBusinessArea(VenueSearchQuery query, String title, String address, String district) {
        if (hasText(query.businessArea())) {
            return query.businessArea().trim();
        }
        if (hasText(query.district()) && (title.contains(query.district()) || address.contains(query.district()))) {
            return query.district().trim();
        }
        return district;
    }

    private CategoryParts categoryParts(String rawCategory, List<String> selectedCategories, String keyword) {
        List<String> segments = new ArrayList<>();
        if (hasText(rawCategory)) {
            for (String part : rawCategory.split("[,:：;；/>|]")) {
                String trimmed = part.trim();
                if (hasText(trimmed) && !"美食".equals(trimmed) && !"餐饮服务".equals(trimmed) && !"餐厅".equals(trimmed)) {
                    segments.add(trimmed);
                }
            }
        }

        String fallback = selectedCategories != null && !selectedCategories.isEmpty()
                ? selectedCategories.getFirst()
                : keyword;

        String subcategory = segments.isEmpty() ? normalizeDisplayCategory(fallback) : normalizeDisplayCategory(segments.getLast());
        String category = normalizeDisplayCategory(segments.isEmpty() ? fallback : segments.getFirst());
        if (!hasText(subcategory)) {
            subcategory = category;
        }
        return new CategoryParts(category, subcategory);
    }

    private String searchKeywordForCategory(String category) {
        if (!hasText(category)) {
            return "餐厅";
        }
        return switch (category.trim()) {
            case "日料" -> "日本料理";
            case "法餐" -> "法国菜";
            case "韩料" -> "韩国料理";
            case "西餐" -> "西餐";
            case "本帮菜" -> "本帮菜";
            case "居酒屋" -> "居酒屋";
            case "寿喜锅" -> "寿喜锅";
            case "日式烧肉", "烧肉" -> "日式烧肉";
            default -> category.trim();
        };
    }

    private String normalizeDisplayCategory(String raw) {
        if (!hasText(raw)) {
            return "餐厅";
        }
        String value = raw.trim();
        if (value.contains("日本") || value.contains("日料") || value.contains("寿司") || value.contains("居酒屋") || value.contains("割烹") || value.contains("寿喜锅")) {
            return "日料";
        }
        if (value.contains("法")) {
            return "法餐";
        }
        if (value.contains("西餐")) {
            return "西餐";
        }
        if (value.contains("本帮")) {
            return "本帮菜";
        }
        if (value.contains("韩国") || value.contains("韩")) {
            return "韩料";
        }
        if (value.contains("火锅")) {
            return "火锅";
        }
        if (value.contains("烧肉") || value.contains("烤")) {
            return "烧烤";
        }
        if (value.contains("咖啡")) {
            return "咖啡";
        }
        return value;
    }

    private int estimateAveragePrice(String category, String subcategory) {
        String value = (subcategory == null ? category : subcategory).trim();
        if (value.contains("割烹")) {
            return 260;
        }
        if (value.contains("居酒屋")) {
            return 120;
        }
        if (value.contains("寿喜锅")) {
            return 188;
        }
        return switch (category) {
            case "日料" -> 158;
            case "本帮菜" -> 96;
            case "中餐" -> 88;
            case "法餐" -> 220;
            case "西餐" -> 168;
            case "韩料" -> 118;
            case "火锅" -> 136;
            case "烧烤" -> 128;
            case "咖啡" -> 58;
            default -> 128;
        };
    }

    private double estimateRating(String category, Integer distanceMeters) {
        double rating = switch (category) {
            case "法餐" -> 4.5;
            case "日料" -> 4.4;
            case "本帮菜" -> 4.2;
            default -> 4.1;
        };
        if (distanceMeters != null) {
            if (distanceMeters <= 500) {
                rating += 0.2;
            } else if (distanceMeters <= 1500) {
                rating += 0.1;
            }
        }
        return Math.max(3.8, Math.min(4.8, rating));
    }

    private int estimateReviewCount(Integer distanceMeters) {
        if (distanceMeters == null) {
            return 120;
        }
        if (distanceMeters <= 500) {
            return 320;
        }
        if (distanceMeters <= 1500) {
            return 180;
        }
        return 96;
    }

    private boolean matches(Venue venue, VenueSearchQuery query) {
        if (query.cityCode() != null && !query.cityCode().isBlank() && !query.cityCode().equalsIgnoreCase(venue.cityCode())) {
            return false;
        }
        if (query.district() != null && !query.district().isBlank() && !query.district().equals(venue.district())) {
            return false;
        }
        if (query.businessArea() != null && !query.businessArea().isBlank() && !query.businessArea().equals(venue.businessArea())) {
            return false;
        }
        if (query.categories() != null && !query.categories().isEmpty()) {
            boolean hit = query.categories().stream().anyMatch(category -> category.equals(venue.category()) || category.equals(venue.subcategory()));
            if (!hit) {
                return false;
            }
        }
        if (query.priceMin() != null && venue.avgPrice() < query.priceMin()) {
            return false;
        }
        if (query.priceMax() != null && venue.avgPrice() > query.priceMax()) {
            return false;
        }
        if (query.ratingMin() != null && venue.rating() < query.ratingMin()) {
            return false;
        }
        if (Boolean.TRUE.equals(query.openNow()) && venue.openStatus() != Venue.OpenStatus.OPEN) {
            return false;
        }
        if (query.radiusMeters() != null && query.latitude() != null && query.longitude() != null) {
            Integer distance = distanceMeters(query.latitude(), query.longitude(), venue);
            if (distance != null && distance > query.radiusMeters()) {
                return false;
            }
        }
        return true;
    }

    private Comparator<Venue> comparatorFor(VenueSearchQuery query) {
        String sortBy = query.sortBy() == null ? "RECOMMENDED" : query.sortBy().toUpperCase(Locale.ROOT);
        return switch (sortBy) {
            case "DISTANCE" -> Comparator.comparing(venue -> nullableDistance(query, venue), Comparator.nullsLast(Integer::compareTo));
            case "RATING" -> Comparator.comparingDouble(Venue::rating).reversed().thenComparingInt(Venue::avgPrice);
            case "PRICE_ASC" -> Comparator.comparingInt(Venue::avgPrice)
                    .thenComparing(Comparator.comparingDouble(Venue::rating).reversed());
            case "PRICE_DESC" -> Comparator.comparingInt(Venue::avgPrice).reversed()
                    .thenComparing(Comparator.comparingDouble(Venue::rating).reversed());
            default -> Comparator.comparingDouble((Venue venue) -> recommendationScore(venue, query)).reversed();
        };
    }

    private Integer nullableDistance(VenueSearchQuery query, Venue venue) {
        return distanceMeters(query.latitude(), query.longitude(), venue);
    }

    private double recommendationScore(Venue venue, VenueSearchQuery query) {
        double score = venue.rating() * 1.4;
        if (venue.openStatus() == Venue.OpenStatus.OPEN) {
            score += 0.5;
        }
        if (query.priceMin() != null && query.priceMax() != null && venue.avgPrice() >= query.priceMin() && venue.avgPrice() <= query.priceMax()) {
            score += 0.6;
        }
        Integer distance = nullableDistance(query, venue);
        if (distance != null) {
            score += Math.max(0, 1 - distance / 3000.0);
        }
        return score;
    }

    public String normalize(String raw) {
        return raw == null ? null : raw.trim().replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private int roundDown(int value, int step) {
        return value - (value % step);
    }

    private int roundUp(int value, int step) {
        return ((value + step - 1) / step) * step;
    }

    private String normalizeProvider(String raw) {
        return raw == null || raw.isBlank() ? "tencent" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBaseUrl(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String cityAlias(String cityCode) {
        if (cityCode == null || cityCode.isBlank()) {
            return "上海";
        }
        return CITY_ALIASES.getOrDefault(cityCode.trim().toLowerCase(Locale.ROOT), cityCode.trim());
    }

    private String joinKeyword(List<String> areaHints, String categoryKeyword) {
        return String.join(" ", areaHints.stream()
                .filter(this::hasText)
                .toList()) + (areaHints.isEmpty() ? "" : " ") + categoryKeyword;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<Venue> seedSampleData() {
        List<Venue> sample = new ArrayList<>();
        sample.add(new Venue(UUID.fromString("11111111-1111-1111-1111-111111111111"), "鸟居烧肉", normalize("鸟居烧肉"), "shanghai", "静安区", "静安寺", "南京西路 1201 号", 31.2282, 121.4487, "日料", "日式烧肉", 138, 4.6, 831, Venue.OpenStatus.OPEN, null, SOURCE_PROVIDER_LOCAL_SAMPLE, "https://m.dianping.com/shopshare/torii-yakiniku", List.of("约会", "烧肉", "收藏热门")));
        sample.add(new Venue(UUID.fromString("22222222-2222-2222-2222-222222222222"), "汤城小厨", normalize("汤城小厨"), "shanghai", "静安区", "静安寺", "愚园路 220 号", 31.2291, 121.4463, "中餐", "本帮菜", 92, 4.3, 516, Venue.OpenStatus.OPEN, null, SOURCE_PROVIDER_LOCAL_SAMPLE, "https://m.dianping.com/shopshare/tangchengxiaochu", List.of("家常菜", "出餐快")));
        sample.add(new Venue(UUID.fromString("33333333-3333-3333-3333-333333333333"), "山葵割烹", normalize("山葵割烹"), "shanghai", "静安区", "南京西路", "奉贤路 58 号", 31.2318, 121.4604, "日料", "割烹", 268, 4.8, 301, Venue.OpenStatus.OPEN, null, SOURCE_PROVIDER_LOCAL_SAMPLE, "https://m.dianping.com/shopshare/wasabi-kappo", List.of("纪念日", "高端")));
        sample.add(new Venue(UUID.fromString("44444444-4444-4444-4444-444444444444"), "炭吉居酒屋", normalize("炭吉居酒屋"), "shanghai", "徐汇区", "常熟路", "常熟路 88 号", 31.2205, 121.4469, "日料", "居酒屋", 118, 4.4, 624, Venue.OpenStatus.CLOSED, null, SOURCE_PROVIDER_LOCAL_SAMPLE, "https://m.dianping.com/shopshare/tanji-izakaya", List.of("夜宵", "小酌")));
        sample.add(new Venue(UUID.fromString("55555555-5555-5555-5555-555555555555"), "炉边和牛寿喜锅", normalize("炉边和牛寿喜锅"), "shanghai", "黄浦区", "新天地", "太仓路 181 弄", 31.2203, 121.4742, "日料", "寿喜锅", 188, 4.7, 287, Venue.OpenStatus.OPEN, null, SOURCE_PROVIDER_LOCAL_SAMPLE, "https://m.dianping.com/shopshare/sukiyaki-house", List.of("和牛", "约会")));
        sample.add(new Venue(UUID.fromString("66666666-6666-6666-6666-666666666666"), "Le Petit Bistro", normalize("Le Petit Bistro"), "shanghai", "静安区", "武定路", "武定路 1023 号", 31.2334, 121.4391, "西餐", "法餐", 210, 4.5, 174, Venue.OpenStatus.OPEN, null, SOURCE_PROVIDER_LOCAL_SAMPLE, "https://m.dianping.com/shopshare/le-petit-bistro", List.of("法餐", "庆祝")));
        return sample;
    }

    public record SearchResult(
            List<Venue> items,
            String searchStrategy,
            String notice
    ) {
    }

    public record FilterMetadata(
            String cityCode,
            List<DistrictFilter> districts,
            List<String> categories,
            List<SortOption> sortOptions,
            NumericRange priceRange,
            List<Double> ratingOptions,
            List<Integer> radiusOptions,
            List<Integer> peopleCountOptions
    ) {
    }

    public record DistrictFilter(
            String name,
            List<String> businessAreas
    ) {
    }

    public record SortOption(
            String value,
            String label
    ) {
    }

    public record NumericRange(
            int min,
            int max,
            int step
    ) {
    }

    private record CategoryParts(
            String category,
            String subcategory
    ) {
    }

    private record BaiduVenueEnhancement(
            Integer avgPrice,
            Double rating,
            Integer reviewCount,
            Venue.OpenStatus openStatus,
            String sourceUrl
    ) {
    }

    private record RemotePoiSearchResult(
            List<Venue> items,
            String searchStrategy,
            String notice,
            boolean fallbackToLocal
    ) {
    }
}
