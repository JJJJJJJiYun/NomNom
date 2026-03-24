package com.nomnom.mvp.domain;

import com.nomnom.mvp.support.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DecisionSession {
    private final UUID id;
    private final UUID userId;
    private final String name;
    private final Context context;
    private final Map<UUID, SeededCandidate> candidates;
    private final List<MatchupResult> history = new ArrayList<>();
    private List<OpenMatchup> currentRoundMatchups = new ArrayList<>();
    private List<UUID> nextRoundVenueIds = new ArrayList<>();
    private int currentRound;
    private Status status;
    private UUID winnerVenueId;
    private final Instant createdAt;

    public DecisionSession(UUID id, UUID userId, String name, Context context, List<SeededCandidate> seededCandidates) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.context = context;
        this.candidates = new LinkedHashMap<>();
        seededCandidates.stream()
                .sorted(Comparator.comparingInt(SeededCandidate::seed))
                .forEach(candidate -> this.candidates.put(candidate.venueId(), candidate));
        this.status = Status.IN_PROGRESS;
        this.createdAt = Instant.now();
        startRound(seededCandidates.stream().sorted(Comparator.comparingInt(SeededCandidate::seed)).map(SeededCandidate::venueId).toList());
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public Context context() {
        return context;
    }

    public int currentRound() {
        return currentRound;
    }

    public Status status() {
        return status;
    }

    public UUID winnerVenueId() {
        return winnerVenueId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<MatchupResult> history() {
        return List.copyOf(history);
    }

    public OpenMatchup nextMatchup() {
        return currentRoundMatchups.stream().filter(matchup -> matchup.status() == MatchupStatus.OPEN).findFirst().orElse(null);
    }

    public List<SeededCandidate> seededCandidates() {
        return List.copyOf(candidates.values());
    }

    public void vote(UUID matchupId, UUID winnerVenueId) {
        int index = -1;
        OpenMatchup target = null;
        for (int i = 0; i < currentRoundMatchups.size(); i++) {
            OpenMatchup matchup = currentRoundMatchups.get(i);
            if (matchup.matchupId().equals(matchupId)) {
                index = i;
                target = matchup;
                break;
            }
        }

        if (target == null || target.status() != MatchupStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MATCHUP_MISMATCH", "Matchup is not available for voting");
        }
        if (!winnerVenueId.equals(target.leftVenueId()) && !winnerVenueId.equals(target.rightVenueId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WINNER", "winnerVenueId must match one of the two candidates");
        }

        OpenMatchup decided = new OpenMatchup(
                target.matchupId(),
                target.roundNo(),
                target.bracketOrder(),
                target.leftVenueId(),
                target.rightVenueId(),
                winnerVenueId,
                MatchupStatus.DECIDED,
                Instant.now()
        );
        currentRoundMatchups.set(index, decided);
        history.add(new MatchupResult(
                decided.matchupId(),
                decided.roundNo(),
                decided.bracketOrder(),
                decided.leftVenueId(),
                decided.rightVenueId(),
                decided.winnerVenueId(),
                decided.decidedAt()
        ));
        nextRoundVenueIds.add(winnerVenueId);

        boolean roundComplete = currentRoundMatchups.stream().allMatch(matchup -> matchup.status() == MatchupStatus.DECIDED);
        if (roundComplete) {
            List<UUID> advancing = List.copyOf(nextRoundVenueIds);
            nextRoundVenueIds = new ArrayList<>();
            startRound(advancing);
        }
    }

    private void startRound(List<UUID> participants) {
        if (participants.isEmpty()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_BRACKET", "No candidates available to continue decision");
        }
        if (participants.size() == 1) {
            currentRoundMatchups = List.of();
            status = Status.COMPLETED;
            winnerVenueId = participants.getFirst();
            return;
        }

        currentRound += 1;
        List<UUID> working = new ArrayList<>(participants);
        List<OpenMatchup> round = new ArrayList<>();

        if (working.size() % 2 == 1) {
            nextRoundVenueIds.add(working.removeFirst());
        }

        int order = 1;
        while (working.size() >= 2) {
            UUID left = working.removeFirst();
            UUID right = working.removeLast();
            round.add(new OpenMatchup(UUID.randomUUID(), currentRound, order++, left, right, null, MatchupStatus.OPEN, null));
        }
        currentRoundMatchups = round;
    }

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    public enum MatchupStatus {
        OPEN,
        DECIDED
    }

    public record Context(
            Integer peopleCount,
            Integer priceMin,
            Integer priceMax,
            Boolean openNow,
            String district,
            String businessArea
    ) {
    }

    public record SeededCandidate(
            UUID venueId,
            int seed,
            double initialScore
    ) {
    }

    public record OpenMatchup(
            UUID matchupId,
            int roundNo,
            int bracketOrder,
            UUID leftVenueId,
            UUID rightVenueId,
            UUID winnerVenueId,
            MatchupStatus status,
            Instant decidedAt
    ) {
    }

    public record MatchupResult(
            UUID matchupId,
            int roundNo,
            int bracketOrder,
            UUID leftVenueId,
            UUID rightVenueId,
            UUID winnerVenueId,
            Instant decidedAt
    ) {
    }
}
