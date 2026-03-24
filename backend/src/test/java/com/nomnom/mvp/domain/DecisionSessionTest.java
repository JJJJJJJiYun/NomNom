package com.nomnom.mvp.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DecisionSessionTest {
    @Test
    void completesBracketAndProducesWinner() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();

        DecisionSession session = new DecisionSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "今晚吃什么",
                new DecisionSession.Context(2, 80, 220, true, "静安区", "静安寺"),
                List.of(
                        new DecisionSession.SeededCandidate(first, 1, 9.2),
                        new DecisionSession.SeededCandidate(second, 2, 8.4),
                        new DecisionSession.SeededCandidate(third, 3, 8.1),
                        new DecisionSession.SeededCandidate(fourth, 4, 7.9)
                )
        );

        DecisionSession.OpenMatchup roundOneMatchup = session.nextMatchup();
        assertNotNull(roundOneMatchup);
        session.vote(roundOneMatchup.matchupId(), roundOneMatchup.leftVenueId());

        DecisionSession.OpenMatchup secondMatchup = session.nextMatchup();
        assertNotNull(secondMatchup);
        session.vote(secondMatchup.matchupId(), secondMatchup.leftVenueId());

        DecisionSession.OpenMatchup finalMatchup = session.nextMatchup();
        assertNotNull(finalMatchup);
        session.vote(finalMatchup.matchupId(), finalMatchup.leftVenueId());

        assertEquals(DecisionSession.Status.COMPLETED, session.status());
        assertNull(session.nextMatchup());
        assertEquals(finalMatchup.leftVenueId(), session.winnerVenueId());
        assertEquals(3, session.history().size());
    }
}
