package com.nomnom.backend;

import java.util.UUID;

public record DecisionRecordView(UUID id, String leftRestaurantName, String rightRestaurantName, String winnerRestaurantName) {}
