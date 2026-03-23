package com.nomnom.backend;

import java.util.List;

public record DecisionResultView(RestaurantCard winner, List<DecisionRecordView> history) {}
