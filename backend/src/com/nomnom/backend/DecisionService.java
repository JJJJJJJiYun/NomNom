package com.nomnom.backend;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DecisionService {
    private final RestaurantCatalogService catalogService;
    private final Map<UUID, DecisionSessionState> sessions = new ConcurrentHashMap<>();

    public DecisionService(RestaurantCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public DecisionResponse createDecision(DecisionContext context, List<UUID> candidateIds) {
        List<Restaurant> candidates = candidateIds == null || candidateIds.isEmpty()
                ? catalogService.search(context)
                : candidateIds.stream().map(id -> catalogService.findById(id).orElseThrow()).toList();
        if (candidates.size() < 2) {
            throw new IllegalArgumentException("At least two restaurants are required");
        }
        DecisionSessionState state = new DecisionSessionState(UUID.randomUUID(), context, candidates);
        sessions.put(state.id(), state);
        return toResponse(state);
    }

    public DecisionResponse vote(UUID sessionId, UUID matchupId, DecisionSessionState.DecisionWinner winner) {
        DecisionSessionState state = requireSession(sessionId);
        if (state.activeMatchup() == null || !state.activeMatchup().matchupId().equals(matchupId)) {
            throw new IllegalArgumentException("Matchup mismatch");
        }
        state.vote(winner);
        return toResponse(state);
    }

    public DecisionResponse getDecision(UUID sessionId) {
        return toResponse(requireSession(sessionId));
    }

    private DecisionSessionState requireSession(UUID sessionId) {
        DecisionSessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalArgumentException("Session not found");
        }
        return state;
    }

    private DecisionResponse toResponse(DecisionSessionState state) {
        MatchupCard matchup = null;
        DecisionResultView result = null;
        if (state.activeMatchup() != null) {
            matchup = new MatchupCard(
                    state.activeMatchup().matchupId(),
                    catalogService.toCard(state.activeMatchup().left(), state.context()),
                    catalogService.toCard(state.activeMatchup().right(), state.context())
            );
        }
        if (state.completed() && state.winner() != null) {
            result = new DecisionResultView(catalogService.toCard(state.winner(), state.context()), state.history());
        }
        return new DecisionResponse(state.id(), state.context(), state.completed() ? "COMPLETED" : "IN_PROGRESS", matchup, result);
    }
}
