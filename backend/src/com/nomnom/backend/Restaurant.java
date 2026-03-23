package com.nomnom.backend;

import java.util.List;
import java.util.UUID;

public record Restaurant(
        UUID id,
        String name,
        String cuisine,
        int averagePrice,
        int distanceMeters,
        double rating,
        boolean openNow,
        boolean favorite,
        String neighborhood,
        String headline,
        List<String> tags,
        ReviewDigest reviewDigest
) {}
