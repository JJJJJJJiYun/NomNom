package com.nomnom.backend;

import java.util.UUID;

public record MatchupCard(UUID matchupId, RestaurantCard left, RestaurantCard right) {}
