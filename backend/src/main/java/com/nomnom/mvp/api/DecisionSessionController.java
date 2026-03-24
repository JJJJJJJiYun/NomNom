package com.nomnom.mvp.api;

import com.nomnom.mvp.domain.DecisionSession;
import com.nomnom.mvp.domain.Venue;
import com.nomnom.mvp.service.AuthService;
import com.nomnom.mvp.service.DecisionSessionService;
import com.nomnom.mvp.service.VenueService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/decision-sessions")
public class DecisionSessionController {
    private final AuthService authService;
    private final DecisionSessionService decisionSessionService;
    private final VenueService venueService;

    public DecisionSessionController(AuthService authService, DecisionSessionService decisionSessionService, VenueService venueService) {
        this.authService = authService;
        this.decisionSessionService = decisionSessionService;
        this.venueService = venueService;
    }

    @PostMapping
    public SessionResponse createSession(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @RequestBody CreateSessionRequest request) {
        UUID userId = authService.requireUserId(authorization);
        DecisionSession session = decisionSessionService.createSession(
                userId,
                request.name(),
                new DecisionSession.Context(
                        request.context() == null ? null : request.context().peopleCount(),
                        request.context() == null ? null : request.context().priceMin(),
                        request.context() == null ? null : request.context().priceMax(),
                        request.context() == null ? null : request.context().openNow(),
                        request.context() == null ? null : request.context().district(),
                        request.context() == null ? null : request.context().businessArea()
                ),
                request.candidateVenueIds()
        );
        return toSessionResponse(session);
    }

    @GetMapping("/{sessionId}")
    public SessionResponse getSession(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                      @PathVariable UUID sessionId) {
        UUID userId = authService.requireUserId(authorization);
        return toSessionResponse(decisionSessionService.getSession(userId, sessionId));
    }

    @PostMapping("/{sessionId}/votes")
    public SessionResponse vote(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                @PathVariable UUID sessionId,
                                @RequestBody VoteRequest request) {
        UUID userId = authService.requireUserId(authorization);
        DecisionSession session = decisionSessionService.vote(userId, sessionId, request.matchupId(), request.winnerVenueId());
        return toSessionResponse(session);
    }

    @GetMapping("/{sessionId}/result")
    public ResultResponse getResult(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                    @PathVariable UUID sessionId) {
        UUID userId = authService.requireUserId(authorization);
        DecisionSession session = decisionSessionService.getSession(userId, sessionId);
        Venue winner = session.winnerVenueId() == null ? null : venueService.get(session.winnerVenueId());
        return new ResultResponse(
                session.id(),
                session.status().name(),
                winner == null ? null : toVenueSummary(winner),
                winner == null ? List.of() : decisionSessionService.winnerReasons(session, winner),
                session.history().stream().map(this::toHistory).toList()
        );
    }

    private SessionResponse toSessionResponse(DecisionSession session) {
        DecisionSession.OpenMatchup next = session.nextMatchup();
        return new SessionResponse(
                session.id(),
                session.status().name(),
                session.currentRound(),
                new ContextResponse(
                        session.context().peopleCount(),
                        session.context().priceMin(),
                        session.context().priceMax(),
                        session.context().openNow(),
                        session.context().district(),
                        session.context().businessArea()
                ),
                session.history().stream().map(this::toHistory).toList(),
                next == null ? null : new MatchupResponse(
                        next.matchupId(),
                        toVenueSummary(venueService.get(next.leftVenueId())),
                        toVenueSummary(venueService.get(next.rightVenueId()))
                )
        );
    }

    private MatchupHistoryResponse toHistory(DecisionSession.MatchupResult result) {
        return new MatchupHistoryResponse(
                result.matchupId(),
                result.roundNo(),
                result.leftVenueId(),
                result.rightVenueId(),
                result.winnerVenueId(),
                result.decidedAt()
        );
    }

    private VenueSummary toVenueSummary(Venue venue) {
        return new VenueSummary(venue.id(), venue.name(), venue.avgPrice(), venue.rating(), venue.openStatus().name());
    }

    public record CreateSessionRequest(
            String name,
            ContextRequest context,
            List<UUID> candidateVenueIds
    ) {
    }

    public record ContextRequest(
            Integer peopleCount,
            Integer priceMin,
            Integer priceMax,
            Boolean openNow,
            String district,
            String businessArea
    ) {
    }

    public record VoteRequest(
            UUID matchupId,
            UUID winnerVenueId
    ) {
    }

    public record SessionResponse(
            UUID sessionId,
            String status,
            int round,
            ContextResponse context,
            List<MatchupHistoryResponse> history,
            MatchupResponse nextMatchup
    ) {
    }

    public record ContextResponse(
            Integer peopleCount,
            Integer priceMin,
            Integer priceMax,
            Boolean openNow,
            String district,
            String businessArea
    ) {
    }

    public record MatchupResponse(
            UUID matchupId,
            VenueSummary left,
            VenueSummary right
    ) {
    }

    public record VenueSummary(
            UUID venueId,
            String name,
            int avgPrice,
            double rating,
            String openStatus
    ) {
    }

    public record MatchupHistoryResponse(
            UUID matchupId,
            int round,
            UUID leftVenueId,
            UUID rightVenueId,
            UUID winnerVenueId,
            Instant decidedAt
    ) {
    }

    public record ResultResponse(
            UUID sessionId,
            String status,
            VenueSummary winner,
            List<String> reasons,
            List<MatchupHistoryResponse> history
    ) {
    }
}
