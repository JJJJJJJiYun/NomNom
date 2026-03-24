package com.nomnom.mvp.service;

import com.nomnom.mvp.domain.Venue;
import com.nomnom.mvp.domain.VenueSearchQuery;
import com.nomnom.mvp.support.ApiException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class VenueRankingService {
    private final ListService listService;
    private final DecisionSessionService decisionSessionService;
    private final VenueService venueService;

    public VenueRankingService(ListService listService, DecisionSessionService decisionSessionService, VenueService venueService) {
        this.listService = listService;
        this.decisionSessionService = decisionSessionService;
        this.venueService = venueService;
    }

    public List<Venue> rankSearchResults(UUID userId, VenueSearchQuery query, List<Venue> venues) {
        SearchProfile profile = buildProfile(userId);
        Comparator<Venue> comparator = comparatorFor(query, profile);
        return venues.stream()
                .sorted(comparator)
                .toList();
    }

    private SearchProfile buildProfile(UUID userId) {
        Set<UUID> favoriteVenueIds = new LinkedHashSet<>(listService.favoriteVenueIds(userId));
        DecisionSessionService.UserDecisionProfile decisionProfile = decisionSessionService.userDecisionProfile(userId);
        Map<String, Double> categoryAffinity = new LinkedHashMap<>();

        favoriteVenueIds.stream()
                .map(this::safeVenue)
                .filter(java.util.Objects::nonNull)
                .forEach(venue -> addAffinity(categoryAffinity, venue, 0.75, 0.45));

        decisionProfile.winnerVenueIds().stream()
                .map(this::safeVenue)
                .filter(java.util.Objects::nonNull)
                .forEach(venue -> addAffinity(categoryAffinity, venue, 0.45, 0.30));

        return new SearchProfile(favoriteVenueIds, decisionProfile.venueStats(), Map.copyOf(categoryAffinity));
    }

    private Comparator<Venue> comparatorFor(VenueSearchQuery query, SearchProfile profile) {
        Comparator<Venue> distanceComparator = Comparator.comparing(
                (Venue venue) -> venueService.distanceMeters(query.latitude(), query.longitude(), venue),
                Comparator.nullsLast(Integer::compareTo)
        );
        Comparator<Venue> ratingComparator = Comparator.comparingDouble(Venue::rating).reversed()
                .thenComparing(Comparator.comparingInt(Venue::reviewCount).reversed())
                .thenComparing(distanceComparator);

        String sortBy = query.sortBy() == null ? "RECOMMENDED" : query.sortBy().toUpperCase(java.util.Locale.ROOT);
        return switch (sortBy) {
            case "DISTANCE" -> distanceComparator
                    .thenComparing(Comparator.comparingDouble((Venue venue) -> recommendationScore(venue, query, profile)).reversed());
            case "RATING" -> ratingComparator;
            case "PRICE_ASC" -> Comparator.comparingInt(Venue::avgPrice)
                    .thenComparing(Comparator.comparingDouble(Venue::rating).reversed())
                    .thenComparing(distanceComparator);
            case "PRICE_DESC" -> Comparator.comparingInt(Venue::avgPrice).reversed()
                    .thenComparing(Comparator.comparingDouble(Venue::rating).reversed())
                    .thenComparing(distanceComparator);
            default -> Comparator.comparingDouble((Venue venue) -> recommendationScore(venue, query, profile)).reversed()
                    .thenComparing(distanceComparator)
                    .thenComparing(ratingComparator)
                    .thenComparingInt(Venue::avgPrice);
        };
    }

    private double recommendationScore(Venue venue, VenueSearchQuery query, SearchProfile profile) {
        double score = venue.rating() * 1.35;
        score += reviewConfidenceScore(venue.reviewCount());
        score += budgetFitnessScore(venue, query);
        score += distanceScore(venue, query);

        if (venue.openStatus() == Venue.OpenStatus.OPEN) {
            score += 0.45;
        }
        if (profile.favoriteVenueIds().contains(venue.id())) {
            score += 0.9;
        }
        if (query.district() != null && query.district().equals(venue.district())) {
            score += 0.2;
        }
        if (query.businessArea() != null && query.businessArea().equals(venue.businessArea())) {
            score += 0.2;
        }

        DecisionSessionService.VenueDecisionStats decisionStats = profile.venueStats().get(venue.id());
        if (decisionStats != null) {
            score += Math.min(1.0, decisionStats.wins() * 0.28);
            score -= Math.min(0.45, decisionStats.losses() * 0.12);
            if (decisionStats.appearances() > 0) {
                score += ((double) decisionStats.wins() / decisionStats.appearances()) * 0.4;
            }
        }

        score += profile.categoryAffinity().getOrDefault(venue.category(), 0.0);
        if (!venue.subcategory().equals(venue.category())) {
            score += profile.categoryAffinity().getOrDefault(venue.subcategory(), 0.0) * 0.8;
        }
        return score;
    }

    private double reviewConfidenceScore(int reviewCount) {
        if (reviewCount <= 0) {
            return 0;
        }
        return Math.min(0.4, Math.log10(reviewCount + 1) * 0.16);
    }

    private double budgetFitnessScore(Venue venue, VenueSearchQuery query) {
        if (query.priceMin() == null || query.priceMax() == null) {
            return 0;
        }
        int min = query.priceMin();
        int max = query.priceMax();
        int center = (min + max) / 2;
        int halfRange = Math.max(20, (max - min) / 2);
        int delta = Math.abs(venue.avgPrice() - center);

        if (venue.avgPrice() >= min && venue.avgPrice() <= max) {
            return 0.75 - Math.min(0.35, delta / (double) halfRange * 0.35);
        }
        return -Math.min(0.5, delta / (double) halfRange * 0.2);
    }

    private double distanceScore(Venue venue, VenueSearchQuery query) {
        Integer distanceMeters = venueService.distanceMeters(query.latitude(), query.longitude(), venue);
        if (distanceMeters == null) {
            return 0;
        }
        int radius = query.radiusMeters() == null ? 3000 : query.radiusMeters();
        return Math.max(0, 1 - distanceMeters / (double) radius) * 0.9;
    }

    private void addAffinity(Map<String, Double> categoryAffinity, Venue venue, double categoryWeight, double subcategoryWeight) {
        mergeAffinity(categoryAffinity, venue.category(), categoryWeight);
        if (venue.subcategory() != null && !venue.subcategory().equals(venue.category())) {
            mergeAffinity(categoryAffinity, venue.subcategory(), subcategoryWeight);
        }
    }

    private void mergeAffinity(Map<String, Double> categoryAffinity, String category, double weight) {
        if (category == null || category.isBlank()) {
            return;
        }
        categoryAffinity.merge(category.trim(), weight, Double::sum);
    }

    private Venue safeVenue(UUID venueId) {
        try {
            return venueService.get(venueId);
        } catch (ApiException ignored) {
            return null;
        }
    }

    private record SearchProfile(
            Set<UUID> favoriteVenueIds,
            Map<UUID, DecisionSessionService.VenueDecisionStats> venueStats,
            Map<String, Double> categoryAffinity
    ) {
    }
}
