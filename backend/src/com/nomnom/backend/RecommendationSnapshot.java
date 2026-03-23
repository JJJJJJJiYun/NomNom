package com.nomnom.backend;

import java.util.List;

public record RecommendationSnapshot(String summary, List<String> reasons, List<String> pros, List<String> cons) {}
