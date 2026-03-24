package com.nomnom.mvp.domain;

import java.util.List;
import java.util.UUID;

public record Venue(
        UUID id,
        String name,
        String normalizedName,
        String cityCode,
        String district,
        String businessArea,
        String address,
        double latitude,
        double longitude,
        String category,
        String subcategory,
        int avgPrice,
        double rating,
        int reviewCount,
        OpenStatus openStatus,
        String coverImageUrl,
        String sourceProvider,
        String sourceUrl,
        List<String> tags
) {
    public enum OpenStatus {
        OPEN,
        CLOSED
    }
}
