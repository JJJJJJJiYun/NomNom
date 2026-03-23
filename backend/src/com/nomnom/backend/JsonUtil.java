package com.nomnom.backend;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonUtil {
    private JsonUtil() {}

    public static String readBody(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static int extractInt(String json, String key, int defaultValue) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultValue;
    }

    public static String extractString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static List<String> extractStringArray(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        if (!matcher.find()) {
            return List.of();
        }
        String content = matcher.group(1).trim();
        if (content.isEmpty()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        Matcher itemMatcher = Pattern.compile("\\\"([^\\\"]*)\\\"").matcher(content);
        while (itemMatcher.find()) {
            items.add(itemMatcher.group(1));
        }
        return items;
    }

    public static List<UUID> extractUuidArray(String json, String key) {
        return extractStringArray(json, key).stream().map(UUID::fromString).toList();
    }

    public static String quote(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static String arrayOfStrings(List<String> items) {
        return items.stream().map(JsonUtil::quote).reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
    }

    public static String contextJson(DecisionContext context) {
        return "{" +
                jsonPair("budgetMin", Integer.toString(context.budgetMin()), false) + "," +
                jsonPair("budgetMax", Integer.toString(context.budgetMax()), false) + "," +
                jsonPair("preferredCuisines", arrayOfStrings(context.preferredCuisines()), false) + "," +
                jsonPair("peopleCount", Integer.toString(context.peopleCount()), false) + "," +
                jsonPair("maxDistanceMeters", Integer.toString(context.maxDistanceMeters()), false) +
                "}";
    }

    public static String restaurantJson(Restaurant restaurant) {
        return "{" +
                jsonPair("id", quote(restaurant.id().toString()), false) + "," +
                jsonPair("name", quote(restaurant.name()), false) + "," +
                jsonPair("cuisine", quote(restaurant.cuisine()), false) + "," +
                jsonPair("averagePrice", Integer.toString(restaurant.averagePrice()), false) + "," +
                jsonPair("distanceMeters", Integer.toString(restaurant.distanceMeters()), false) + "," +
                jsonPair("rating", Double.toString(restaurant.rating()), false) + "," +
                jsonPair("isOpenNow", Boolean.toString(restaurant.openNow()), false) + "," +
                jsonPair("isFavorite", Boolean.toString(restaurant.favorite()), false) + "," +
                jsonPair("neighborhood", quote(restaurant.neighborhood()), false) + "," +
                jsonPair("headline", quote(restaurant.headline()), false) + "," +
                jsonPair("tags", arrayOfStrings(restaurant.tags()), false) + "," +
                jsonPair("reviewDigest", reviewDigestJson(restaurant.reviewDigest()), false) +
                "}";
    }

    public static String reviewDigestJson(ReviewDigest digest) {
        return "{" +
                jsonPair("bestFor", arrayOfStrings(digest.bestFor()), false) + "," +
                jsonPair("pros", arrayOfStrings(digest.pros()), false) + "," +
                jsonPair("cons", arrayOfStrings(digest.cons()), false) +
                "}";
    }

    public static String snapshotJson(RecommendationSnapshot snapshot) {
        return "{" +
                jsonPair("summary", quote(snapshot.summary()), false) + "," +
                jsonPair("reasons", arrayOfStrings(snapshot.reasons()), false) + "," +
                jsonPair("pros", arrayOfStrings(snapshot.pros()), false) + "," +
                jsonPair("cons", arrayOfStrings(snapshot.cons()), false) +
                "}";
    }

    public static String restaurantCardJson(RestaurantCard card) {
        return "{" +
                jsonPair("restaurant", restaurantJson(card.restaurant()), false) + "," +
                jsonPair("snapshot", snapshotJson(card.snapshot()), false) +
                "}";
    }

    public static String matchupJson(MatchupCard matchup) {
        return "{" +
                jsonPair("matchupId", quote(matchup.matchupId().toString()), false) + "," +
                jsonPair("left", restaurantCardJson(matchup.left()), false) + "," +
                jsonPair("right", restaurantCardJson(matchup.right()), false) +
                "}";
    }

    public static String decisionRecordJson(DecisionRecordView record) {
        return "{" +
                jsonPair("id", quote(record.id().toString()), false) + "," +
                jsonPair("leftRestaurantName", quote(record.leftRestaurantName()), false) + "," +
                jsonPair("rightRestaurantName", quote(record.rightRestaurantName()), false) + "," +
                jsonPair("winnerRestaurantName", quote(record.winnerRestaurantName()), false) +
                "}";
    }

    public static String decisionResultJson(DecisionResultView result) {
        String historyJson = result.history().stream().map(JsonUtil::decisionRecordJson).reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
        return "{" +
                jsonPair("winner", restaurantCardJson(result.winner()), false) + "," +
                jsonPair("history", historyJson, false) +
                "}";
    }

    public static String decisionResponseJson(DecisionResponse response) {
        return "{" +
                jsonPair("sessionId", quote(response.sessionId().toString()), false) + "," +
                jsonPair("context", contextJson(response.context()), false) + "," +
                jsonPair("status", quote(response.status()), false) + "," +
                jsonPair("nextMatchup", response.nextMatchup() == null ? "null" : matchupJson(response.nextMatchup()), false) + "," +
                jsonPair("result", response.result() == null ? "null" : decisionResultJson(response.result()), false) +
                "}";
    }

    public static String restaurantSearchJson(DecisionContext context, List<RestaurantCard> restaurants) {
        String restaurantJson = restaurants.stream().map(JsonUtil::restaurantCardJson).reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");
        return "{" +
                jsonPair("context", contextJson(context), false) + "," +
                jsonPair("restaurants", restaurantJson, false) +
                "}";
    }

    private static String jsonPair(String key, String value, boolean quoteValue) {
        return quote(key) + ":" + (quoteValue ? quote(value) : value);
    }
}
