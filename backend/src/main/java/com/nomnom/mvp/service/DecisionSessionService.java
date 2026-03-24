package com.nomnom.mvp.service;

import com.nomnom.mvp.domain.DecisionSession;
import com.nomnom.mvp.domain.Venue;
import com.nomnom.mvp.support.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DecisionSessionService {
    private final Map<UUID, DecisionSession> sessions = new ConcurrentHashMap<>();
    private final VenueService venueService;
    private final ListService listService;

    public DecisionSessionService(VenueService venueService, ListService listService) {
        this.venueService = venueService;
        this.listService = listService;
    }

    public DecisionSession createSession(UUID userId, String name, DecisionSession.Context context, List<UUID> candidateVenueIds) {
        List<UUID> uniqueIds = candidateVenueIds.stream().distinct().toList();
        if (uniqueIds.size() < 2 || uniqueIds.size() > 32) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CANDIDATE_COUNT", "candidateVenueIds must contain between 2 and 32 unique venues");
        }

        List<Venue> venues = venueService.getAll(uniqueIds);
        List<RankedVenue> rankedVenues = venues.stream()
                .map(venue -> new RankedVenue(venue.id(), seedScore(venue, context, userId)))
                .sorted(Comparator.comparingDouble(RankedVenue::score).reversed())
                .toList();
        List<DecisionSession.SeededCandidate> seeded = new java.util.ArrayList<>();
        for (int i = 0; i < rankedVenues.size(); i++) {
            RankedVenue rankedVenue = rankedVenues.get(i);
            seeded.add(new DecisionSession.SeededCandidate(rankedVenue.venueId(), i + 1, rankedVenue.score()));
        }

        DecisionSession session = new DecisionSession(UUID.randomUUID(), userId, name, context, seeded);
        sessions.put(session.id(), session);
        return session;
    }

    public DecisionSession getSession(UUID userId, UUID sessionId) {
        DecisionSession session = sessions.get(sessionId);
        if (session == null || !session.userId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DECISION_SESSION_NOT_FOUND", "Decision session not found");
        }
        return session;
    }

    public DecisionSession vote(UUID userId, UUID sessionId, UUID matchupId, UUID winnerVenueId) {
        DecisionSession session = getSession(userId, sessionId);
        session.vote(matchupId, winnerVenueId);
        return session;
    }

    public List<String> winnerReasons(DecisionSession session, Venue winner) {
        List<String> reasons = new java.util.ArrayList<>();
        if (winner.rating() >= 4.5) {
            reasons.add("评分更高");
        }
        if (winner.openStatus() == Venue.OpenStatus.OPEN) {
            reasons.add("当前营业中");
        }
        if (listService.contains(session.userId(), winner.id())) {
            reasons.add("来自你的收藏列表");
        }
        if (session.context().priceMin() != null && session.context().priceMax() != null
                && winner.avgPrice() >= session.context().priceMin()
                && winner.avgPrice() <= session.context().priceMax()) {
            reasons.add("价格落在本轮预算区间");
        }
        if (session.context().district() != null && session.context().district().equals(winner.district())) {
            reasons.add("命中当前筛选区域");
        }
        return reasons.isEmpty() ? List.of("在多轮比较中整体更符合当前偏好") : reasons;
    }

    public UserDecisionProfile userDecisionProfile(UUID userId) {
        Map<UUID, MutableVenueDecisionStats> stats = new LinkedHashMap<>();
        List<UUID> winnerVenueIds = new java.util.ArrayList<>();

        sessions.values().stream()
                .filter(session -> session.userId().equals(userId))
                .sorted(Comparator.comparing(DecisionSession::createdAt))
                .forEach(session -> {
                    if (session.winnerVenueId() != null) {
                        winnerVenueIds.add(session.winnerVenueId());
                    }
                    for (DecisionSession.MatchupResult result : session.history()) {
                        mutableStats(stats, result.leftVenueId()).appearances += 1;
                        mutableStats(stats, result.rightVenueId()).appearances += 1;
                        mutableStats(stats, result.winnerVenueId()).wins += 1;
                        UUID loserVenueId = result.winnerVenueId().equals(result.leftVenueId())
                                ? result.rightVenueId()
                                : result.leftVenueId();
                        mutableStats(stats, loserVenueId).losses += 1;
                    }
                });

        Map<UUID, VenueDecisionStats> immutableStats = stats.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new VenueDecisionStats(
                                entry.getValue().appearances,
                                entry.getValue().wins,
                                entry.getValue().losses
                        ),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return new UserDecisionProfile(immutableStats, List.copyOf(winnerVenueIds));
    }

    private double seedScore(Venue venue, DecisionSession.Context context, UUID userId) {
        double score = venue.rating() * 1.5;
        if (venue.openStatus() == Venue.OpenStatus.OPEN) {
            score += 0.5;
        }
        if (listService.contains(userId, venue.id())) {
            score += 0.4;
        }
        if (context.priceMin() != null && context.priceMax() != null
                && venue.avgPrice() >= context.priceMin()
                && venue.avgPrice() <= context.priceMax()) {
            score += 0.5;
        }
        if (context.district() != null && context.district().equals(venue.district())) {
            score += 0.3;
        }
        return score;
    }

    private MutableVenueDecisionStats mutableStats(Map<UUID, MutableVenueDecisionStats> stats, UUID venueId) {
        return stats.computeIfAbsent(venueId, ignored -> new MutableVenueDecisionStats());
    }

    private record RankedVenue(UUID venueId, double score) {
    }

    public record UserDecisionProfile(
            Map<UUID, VenueDecisionStats> venueStats,
            List<UUID> winnerVenueIds
    ) {
    }

    public record VenueDecisionStats(
            int appearances,
            int wins,
            int losses
    ) {
    }

    private static final class MutableVenueDecisionStats {
        int appearances;
        int wins;
        int losses;
    }
}
