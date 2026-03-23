package com.nomnom.backend;

import java.util.UUID;

public record DecisionResponse(UUID sessionId, DecisionContext context, String status, MatchupCard nextMatchup, DecisionResultView result) {}
