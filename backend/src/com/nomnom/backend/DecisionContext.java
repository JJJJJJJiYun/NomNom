package com.nomnom.backend;

import java.util.List;

public record DecisionContext(int budgetMin, int budgetMax, List<String> preferredCuisines, int peopleCount, int maxDistanceMeters) {}
