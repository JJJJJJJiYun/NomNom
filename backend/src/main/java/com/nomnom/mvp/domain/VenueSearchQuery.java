package com.nomnom.mvp.domain;

import java.util.List;

public record VenueSearchQuery(
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
    public int safePage() {
        return page == null || page < 1 ? 1 : page;
    }

    public int safePageSize() {
        if (pageSize == null) {
            return 20;
        }
        return Math.min(Math.max(pageSize, 1), 50);
    }
}
