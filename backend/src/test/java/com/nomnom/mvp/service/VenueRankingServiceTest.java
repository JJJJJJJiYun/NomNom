package com.nomnom.mvp.service;

import com.nomnom.mvp.domain.DecisionSession;
import com.nomnom.mvp.domain.Venue;
import com.nomnom.mvp.domain.VenueSearchQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VenueRankingServiceTest {
    @Test
    void sortsPricesInTheExpectedDirection() {
        VenueService venueService = new VenueService();
        ListService listService = new ListService();
        DecisionSessionService decisionSessionService = new DecisionSessionService(venueService, listService);
        VenueRankingService rankingService = new VenueRankingService(listService, decisionSessionService, venueService);
        UUID userId = UUID.randomUUID();

        Venue cheap = venueService.createManualVenue("Budget Bento", "日料", 60, "静安区", "静安寺", "陕西北路 1 号", List.of(), "MANUAL", null);
        Venue mid = venueService.createManualVenue("Comfort Kitchen", "本帮菜", 120, "静安区", "静安寺", "陕西北路 2 号", List.of(), "MANUAL", null);
        Venue premium = venueService.createManualVenue("Celebration Table", "法餐", 220, "静安区", "静安寺", "陕西北路 3 号", List.of(), "MANUAL", null);
        List<Venue> venues = List.of(premium, cheap, mid);

        List<Venue> ascending = rankingService.rankSearchResults(userId, new VenueSearchQuery(
                "shanghai", 31.2281, 121.4547, 3000, null, null, null, null, null, null, true, "PRICE_ASC", 1, 20
        ), venues);
        List<Venue> descending = rankingService.rankSearchResults(userId, new VenueSearchQuery(
                "shanghai", 31.2281, 121.4547, 3000, null, null, null, null, null, null, true, "PRICE_DESC", 1, 20
        ), venues);

        assertEquals(List.of(cheap.id(), mid.id(), premium.id()), ascending.stream().map(Venue::id).toList());
        assertEquals(List.of(premium.id(), mid.id(), cheap.id()), descending.stream().map(Venue::id).toList());
    }

    @Test
    void recommendationLearnsFromFavoritesAndPastWins() {
        VenueService venueService = new VenueService();
        ListService listService = new ListService();
        DecisionSessionService decisionSessionService = new DecisionSessionService(venueService, listService);
        VenueRankingService rankingService = new VenueRankingService(listService, decisionSessionService, venueService);
        UUID userId = UUID.randomUUID();

        Venue favoriteJapanese = venueService.createManualVenue("Tokyo Hearth", "日料", 150, "静安区", "静安寺", "南京西路 1 号", List.of(), "MANUAL", null);
        Venue similarJapanese = venueService.createManualVenue("Izakaya Lane", "日料", 150, "静安区", "静安寺", "南京西路 2 号", List.of(), "MANUAL", null);
        Venue western = venueService.createManualVenue("Bistro North", "法餐", 150, "静安区", "静安寺", "南京西路 3 号", List.of(), "MANUAL", null);

        listService.addItem(userId, listService.defaultListId(userId), favoriteJapanese.id(), "MANUAL", null, null);
        DecisionSession session = decisionSessionService.createSession(
                userId,
                "Dinner",
                new DecisionSession.Context(2, 100, 220, true, "静安区", null),
                List.of(favoriteJapanese.id(), western.id())
        );
        decisionSessionService.vote(userId, session.id(), session.nextMatchup().matchupId(), favoriteJapanese.id());

        List<Venue> ranked = rankingService.rankSearchResults(userId, new VenueSearchQuery(
                "shanghai", 31.2281, 121.4547, 3000, "静安区", null, null, 100, 220, 4.0, true, "RECOMMENDED", 1, 20
        ), List.of(western, similarJapanese));

        assertEquals(similarJapanese.id(), ranked.getFirst().id());
    }
}
