package com.nomnom.mvp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nomnom.mvp.domain.Venue;
import com.nomnom.mvp.domain.VenueSearchQuery;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VenueServiceTest {
    @Test
    void buildsFilterMetadataFromCurrentVenueCatalog() {
        VenueService venueService = new VenueService();

        VenueService.FilterMetadata metadata = venueService.filterMetadata("shanghai");

        assertEquals("shanghai", metadata.cityCode());
        assertTrue(metadata.districts().stream().anyMatch(district -> district.name().equals("静安区")));
        assertTrue(metadata.districts().stream()
                .filter(district -> district.name().equals("静安区"))
                .flatMap(district -> district.businessAreas().stream())
                .anyMatch("静安寺"::equals));
        assertTrue(metadata.categories().contains("日料"));
        assertTrue(metadata.categories().contains("法餐"));
        assertEquals(90, metadata.priceRange().min());
        assertEquals(270, metadata.priceRange().max());
        assertTrue(metadata.radiusOptions().contains(3000));
    }

    @Test
    void fallsBackToLocalCacheWhenTencentKeyMissing() {
        VenueService venueService = new VenueService();

        VenueService.SearchResult result = venueService.search(new VenueSearchQuery(
                "shanghai",
                31.2281,
                121.4547,
                3000,
                null,
                null,
                List.of("日料"),
                0,
                500,
                4.0,
                true,
                "RECOMMENDED",
                1,
                20
        ));

        assertEquals("LOCAL_CACHE", result.searchStrategy());
        assertTrue(result.notice().contains("TENCENT_MAPS_API_KEY"));
        assertFalse(result.items().isEmpty());
        assertTrue(result.items().stream().map(Venue::sourceProvider).allMatch("LOCAL_SAMPLE"::equals));
    }

    @Test
    void mapsTencentPoiPayloadIntoSearchResults() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> lastQuery = new AtomicReference<>();
        server.createContext("/ws/place/v1/search", exchange -> writeTencentSearchResponse(exchange, lastQuery));
        server.start();

        try {
            VenueService venueService = new VenueService(
                    new ObjectMapper(),
                    "tencent",
                    "demo-key",
                    "demo-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    false
            );

            VenueService.SearchResult result = venueService.search(new VenueSearchQuery(
                    "shanghai",
                    31.2281,
                    121.4547,
                    3000,
                    "静安区",
                    "静安寺",
                    List.of("日料"),
                    90,
                    300,
                    4.0,
                    true,
                    "DISTANCE",
                    1,
                    20
            ));

            assertEquals("REMOTE_TENCENT", result.searchStrategy());
            assertNull(result.notice());
            assertEquals(1, result.items().size());

            Venue venue = result.items().getFirst();
            assertEquals("Torii Yakiniku", venue.name());
            assertEquals("静安区", venue.district());
            assertEquals("静安寺", venue.businessArea());
            assertEquals("TENCENT_LBS", venue.sourceProvider());
            assertTrue(venue.rating() >= 4.0);
            assertTrue(lastQuery.get().contains("sig="));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackGracefullyWhenTencentQuotaIsExceeded() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ws/place/v1/search", this::writeTencentQuotaExceededResponse);
        server.start();

        try {
            VenueService venueService = new VenueService(
                    new ObjectMapper(),
                    "tencent",
                    "demo-key",
                    "demo-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    true
            );

            VenueService.SearchResult result = venueService.search(new VenueSearchQuery(
                    "shanghai",
                    31.2281,
                    121.4547,
                    3000,
                    "静安区",
                    null,
                    List.of("日料"),
                    90,
                    300,
                    4.0,
                    true,
                    "RECOMMENDED",
                    1,
                    20
            ));

            assertEquals("LOCAL_CACHE", result.searchStrategy());
            assertTrue(result.notice().contains("当日额度已用尽"));
            assertFalse(result.items().isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsAmapPoiPayloadIntoSearchResults() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v3/place/around", this::writeAmapSearchResponse);
        server.start();

        try {
            VenueService venueService = new VenueService(
                    new ObjectMapper(),
                    "amap",
                    "demo-amap-key",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "",
                    "",
                    "https://apis.map.qq.com",
                    "",
                    "https://api.map.baidu.com",
                    false,
                    true
            );

            VenueService.SearchResult result = venueService.search(new VenueSearchQuery(
                    "shanghai",
                    31.2281,
                    121.4547,
                    3000,
                    "静安区",
                    "静安寺",
                    List.of("日料"),
                    90,
                    300,
                    4.0,
                    true,
                    "DISTANCE",
                    1,
                    20
            ));

            assertEquals("REMOTE_AMAP", result.searchStrategy());
            assertNull(result.notice());
            assertEquals(1, result.items().size());

            Venue venue = result.items().getFirst();
            assertEquals("燃鸟烧肉酒场", venue.name());
            assertEquals("静安区", venue.district());
            assertEquals("静安寺", venue.businessArea());
            assertEquals("AMAP_LBS", venue.sourceProvider());
            assertEquals(168, venue.avgPrice());
            assertEquals(4.6, venue.rating());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void enrichesAmapResultsWithBaiduDetailsWhenConfigured() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v3/place/around", this::writeAmapSearchResponse);
        server.createContext("/place/v2/search", this::writeBaiduSearchResponse);
        server.start();

        try {
            VenueService venueService = new VenueService(
                    new ObjectMapper(),
                    "amap",
                    "demo-amap-key",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "",
                    "",
                    "https://apis.map.qq.com",
                    "demo-baidu-key",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    false,
                    true
            );

            VenueService.SearchResult result = venueService.search(new VenueSearchQuery(
                    "shanghai",
                    31.2281,
                    121.4547,
                    3000,
                    "静安区",
                    "静安寺",
                    List.of("日料"),
                    90,
                    300,
                    4.0,
                    true,
                    "DISTANCE",
                    1,
                    20
            ));

            assertEquals("REMOTE_AMAP", result.searchStrategy());
            Venue venue = result.items().getFirst();
            assertEquals(198, venue.avgPrice());
            assertEquals(4.9, venue.rating());
            assertEquals(886, venue.reviewCount());
            assertEquals("https://map.baidu.com/poi/%E7%87%83%E9%B8%9F%E7%83%A7%E8%82%89", venue.sourceUrl());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void buildsAmapFilterMetadataFromRemoteDistrictsAndPoiFacets() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v3/place/around", this::writeAmapFacetResponse);
        server.createContext("/v3/config/district", this::writeAmapDistrictResponse);
        server.start();

        try {
            VenueService venueService = new VenueService(
                    new ObjectMapper(),
                    "amap",
                    "demo-amap-key",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "",
                    "",
                    "https://apis.map.qq.com",
                    "",
                    "https://api.map.baidu.com",
                    false,
                    true
            );

            VenueService.FilterMetadata metadata = venueService.filterMetadata(new VenueSearchQuery(
                    "shanghai",
                    31.2281,
                    121.4547,
                    3000,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    1,
                    20
            ));

            assertEquals("shanghai", metadata.cityCode());
            assertTrue(metadata.districts().stream().anyMatch(district -> district.name().equals("静安区")));
            assertTrue(metadata.districts().stream()
                    .filter(district -> district.name().equals("静安区"))
                    .flatMap(district -> district.businessAreas().stream())
                    .anyMatch("静安寺"::equals));
            assertTrue(metadata.categories().contains("日料"));
            assertTrue(metadata.categories().contains("火锅"));
            assertEquals(110, metadata.priceRange().min());
            assertEquals(190, metadata.priceRange().max());
        } finally {
            server.stop(0);
        }
    }

    private void writeTencentSearchResponse(HttpExchange exchange, AtomicReference<String> lastQuery) throws IOException {
        lastQuery.set(exchange.getRequestURI().getRawQuery());
        byte[] payload = """
                {
                  "status": 0,
                  "data": [
                    {
                      "id": "poi-001",
                      "title": "Torii Yakiniku",
                      "address": "南京西路 1201 号",
                      "category": "美食:日本料理:烧肉",
                      "location": {
                        "lat": 31.2282,
                        "lng": 121.4487
                      },
                      "ad_info": {
                        "district": "静安区"
                      },
                      "_distance": 420
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private void writeTencentQuotaExceededResponse(HttpExchange exchange) throws IOException {
        byte[] payload = """
                {
                  "status": 120,
                  "message": "此key每日调用量已达到上限"
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private void writeAmapSearchResponse(HttpExchange exchange) throws IOException {
        byte[] payload = """
                {
                  "status": "1",
                  "info": "OK",
                  "pois": [
                    {
                      "id": "amap-poi-001",
                      "name": "燃鸟烧肉酒场",
                      "address": "南京西路 999 号",
                      "type": "餐饮服务;日本料理;日式烧烤",
                      "location": "121.4487,31.2282",
                      "adname": "静安区",
                      "business_area": "静安寺",
                      "distance": "420",
                      "biz_ext": {
                        "rating": "4.6",
                        "cost": "168"
                      },
                      "photos": [
                        {
                          "url": "https://example.com/amap-yakiniku.jpg"
                        }
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private void writeAmapFacetResponse(HttpExchange exchange) throws IOException {
        byte[] payload = """
                {
                  "status": "1",
                  "info": "OK",
                  "pois": [
                    {
                      "id": "amap-poi-101",
                      "name": "山海炉端",
                      "address": "南京西路 888 号",
                      "type": "餐饮服务;日本料理;日式烧烤",
                      "location": "121.4487,31.2282",
                      "adname": "静安区",
                      "business_area": "静安寺",
                      "distance": "360",
                      "biz_ext": {
                        "rating": "4.5",
                        "cost": "110"
                      }
                    },
                    {
                      "id": "amap-poi-102",
                      "name": "汤锅会馆",
                      "address": "肇嘉浜路 188 号",
                      "type": "餐饮服务;火锅;川味火锅",
                      "location": "121.4450,31.2101",
                      "adname": "徐汇区",
                      "business_area": "徐家汇",
                      "distance": "1280",
                      "biz_ext": {
                        "rating": "4.3",
                        "cost": "190"
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private void writeBaiduSearchResponse(HttpExchange exchange) throws IOException {
        byte[] payload = """
                {
                  "status": 0,
                  "message": "ok",
                  "results": [
                    {
                      "name": "燃鸟烧肉酒场",
                      "address": "静安区 南京西路 999 号",
                      "location": {
                        "lat": 31.22825,
                        "lng": 121.44879
                      },
                      "detail_info": {
                        "overall_rating": "4.9",
                        "price": "198",
                        "comment_num": "886",
                        "status": "营业中",
                        "detail_url": "https://map.baidu.com/poi/%E7%87%83%E9%B8%9F%E7%83%A7%E8%82%89",
                        "tag": "美食;日本菜"
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private void writeAmapDistrictResponse(HttpExchange exchange) throws IOException {
        byte[] payload = """
                {
                  "status": "1",
                  "info": "OK",
                  "districts": [
                    {
                      "name": "上海市",
                      "level": "city",
                      "districts": [
                        {
                          "name": "静安区",
                          "level": "district",
                          "districts": []
                        },
                        {
                          "name": "徐汇区",
                          "level": "district",
                          "districts": []
                        }
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
