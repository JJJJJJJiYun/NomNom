package com.nomnom.mvp.api;

import com.nomnom.mvp.domain.Venue;
import com.nomnom.mvp.domain.VenueSearchQuery;
import com.nomnom.mvp.service.AuthService;
import com.nomnom.mvp.service.ListService;
import com.nomnom.mvp.service.VenueRankingService;
import com.nomnom.mvp.service.VenueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {
    private final AuthService authService;
    private final VenueService venueService;
    private final VenueRankingService venueRankingService;
    private final ListService listService;

    public VenueController(AuthService authService, VenueService venueService, VenueRankingService venueRankingService, ListService listService) {
        this.authService = authService;
        this.venueService = venueService;
        this.venueRankingService = venueRankingService;
        this.listService = listService;
    }

    @GetMapping("/filters")
    public FilterMetadataResponse filters(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                          @RequestParam(required = false) String cityCode,
                                          @RequestParam(required = false) Double latitude,
                                          @RequestParam(required = false) Double longitude,
                                          @RequestParam(required = false) Integer radiusMeters,
                                          @RequestParam(required = false) String district,
                                          @RequestParam(required = false) String businessArea,
                                          @RequestParam(required = false) List<String> categories) {
        authService.requireUserId(authorization);
        VenueService.FilterMetadata metadata = venueService.filterMetadata(
                new VenueSearchQuery(
                        cityCode,
                        latitude,
                        longitude,
                        radiusMeters,
                        district,
                        businessArea,
                        categories,
                        null,
                        null,
                        null,
                        null,
                        null,
                        1,
                        20
                )
        );
        return new FilterMetadataResponse(
                metadata.cityCode(),
                metadata.districts().stream()
                        .map(option -> new DistrictOption(option.name(), option.businessAreas()))
                        .toList(),
                metadata.categories(),
                metadata.sortOptions().stream()
                        .map(sort -> new SortOption(sort.value(), sort.label()))
                        .toList(),
                new NumericRange(metadata.priceRange().min(), metadata.priceRange().max(), metadata.priceRange().step()),
                metadata.ratingOptions(),
                metadata.radiusOptions(),
                metadata.peopleCountOptions()
        );
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                 @RequestBody SearchRequest request) {
        UUID userId = authService.requireUserId(authorization);
        VenueSearchQuery query = request.toQuery();
        VenueService.SearchResult searchResult = venueService.search(query);
        List<Venue> allMatches = venueRankingService.rankSearchResults(userId, query, searchResult.items());
        int page = query.safePage();
        int pageSize = query.safePageSize();
        int fromIndex = Math.min((page - 1) * pageSize, allMatches.size());
        int toIndex = Math.min(fromIndex + pageSize, allMatches.size());
        List<VenueItem> items = allMatches.subList(fromIndex, toIndex).stream()
                .map(venue -> new VenueItem(
                        venue.id(),
                        venue.name(),
                        venue.coverImageUrl(),
                        venue.category(),
                        venue.subcategory(),
                        venue.avgPrice(),
                        venue.rating(),
                        venue.reviewCount(),
                        venueService.distanceMeters(query.latitude(), query.longitude(), venue),
                        venue.openStatus().name(),
                        venue.district(),
                        venue.businessArea(),
                        venue.address(),
                        venue.sourceProvider(),
                        venue.sourceUrl(),
                        listService.contains(userId, venue.id())
                ))
                .toList();
        return new SearchResponse(items, page, pageSize, toIndex < allMatches.size(), searchResult.searchStrategy(), searchResult.notice());
    }

    public record SearchRequest(
            String cityCode,
            Double latitude,
            Double longitude,
            Integer radiusMeters,
            String district,
            String businessArea,
            List<String> categories,
            Integer priceMin,
            Integer priceMax,
            Double ratingMin,
            Boolean openNow,
            String sortBy,
            Integer page,
            Integer pageSize
    ) {
        public VenueSearchQuery toQuery() {
            return new VenueSearchQuery(cityCode, latitude, longitude, radiusMeters, district, businessArea, categories, priceMin, priceMax, ratingMin, openNow, sortBy, page, pageSize);
        }
    }

    public record SearchResponse(
            List<VenueItem> items,
            int page,
            int pageSize,
            boolean hasMore,
            String searchStrategy,
            String notice
    ) {
    }

    public record FilterMetadataResponse(
            String cityCode,
            List<DistrictOption> districts,
            List<String> categories,
            List<SortOption> sortOptions,
            NumericRange priceRange,
            List<Double> ratingOptions,
            List<Integer> radiusOptions,
            List<Integer> peopleCountOptions
    ) {
    }

    public record DistrictOption(
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

    public record VenueItem(
            UUID venueId,
            String name,
            String coverImageUrl,
            String category,
            String subcategory,
            int avgPrice,
            double rating,
            int reviewCount,
            Integer distanceMeters,
            String openStatus,
            String district,
            String businessArea,
            String address,
            String sourceProvider,
            String sourceUrl,
            boolean isInFavorites
    ) {
    }
}
