package com.nomnom.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class NomNomServer {
    private final RestaurantCatalogService catalogService = new RestaurantCatalogService();
    private final DecisionService decisionService = new DecisionService(catalogService);

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        new NomNomServer().start(port);
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/health", this::handleHealth);
        server.createContext("/api/v1/restaurants", this::handleRestaurants);
        server.createContext("/api/v1/decisions", this::handleDecisions);
        server.setExecutor(null);
        server.start();
        System.out.println("NomNom backend started on http://127.0.0.1:" + port);
    }


    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        send(exchange, 200, "{\"status\":\"ok\",\"service\":\"nomnom-backend\"}");
    }

    private void handleRestaurants(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, List<String>> query = queryParams(exchange.getRequestURI());
        int budgetMin = parseInt(query.get("budgetMin"), 80);
        int budgetMax = parseInt(query.get("budgetMax"), 180);
        int peopleCount = parseInt(query.get("peopleCount"), 2);
        int maxDistanceMeters = parseInt(query.get("maxDistanceMeters"), 1500);
        List<String> cuisines = query.getOrDefault("preferredCuisines", List.of("日式烧肉", "居酒屋", "割烹日料"));
        DecisionContext context = new DecisionContext(budgetMin, budgetMax, cuisines, peopleCount, maxDistanceMeters);
        String payload = JsonUtil.restaurantSearchJson(context, catalogService.search(context).stream().map(r -> catalogService.toCard(r, context)).toList());
        send(exchange, 200, payload);
    }

    private void handleDecisions(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length == 4) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    send(exchange, 405, "{\"error\":\"Method not allowed\"}");
                    return;
                }
                String body = JsonUtil.readBody(exchange.getRequestBody());
                DecisionContext context = new DecisionContext(
                        JsonUtil.extractInt(body, "budgetMin", 80),
                        JsonUtil.extractInt(body, "budgetMax", 180),
                        JsonUtil.extractStringArray(body, "preferredCuisines"),
                        JsonUtil.extractInt(body, "peopleCount", 2),
                        JsonUtil.extractInt(body, "maxDistanceMeters", 1500)
                );
                var response = decisionService.createDecision(context, JsonUtil.extractUuidArray(body, "candidateIds"));
                send(exchange, 200, JsonUtil.decisionResponseJson(response));
                return;
            }
            if (parts.length == 5) {
                UUID sessionId = UUID.fromString(parts[4]);
                if (!"GET".equals(exchange.getRequestMethod())) {
                    send(exchange, 405, "{\"error\":\"Method not allowed\"}");
                    return;
                }
                send(exchange, 200, JsonUtil.decisionResponseJson(decisionService.getDecision(sessionId)));
                return;
            }
            if (parts.length == 6 && "vote".equals(parts[5])) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    send(exchange, 405, "{\"error\":\"Method not allowed\"}");
                    return;
                }
                UUID sessionId = UUID.fromString(parts[4]);
                String body = JsonUtil.readBody(exchange.getRequestBody());
                UUID matchupId = UUID.fromString(JsonUtil.extractString(body, "matchupId"));
                String winner = JsonUtil.extractString(body, "winner");
                var response = decisionService.vote(sessionId, matchupId, DecisionSessionState.DecisionWinner.valueOf(winner));
                send(exchange, 200, JsonUtil.decisionResponseJson(response));
                return;
            }
            send(exchange, 404, "{\"error\":\"Not found\"}");
        } catch (Exception exception) {
            send(exchange, 400, "{\"error\":" + JsonUtil.quote(exception.getMessage() == null ? "Invalid request" : exception.getMessage()) + "}");
        }
    }

    private static Map<String, List<String>> queryParams(URI uri) {
        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return Map.of();
        }
        return List.of(uri.getRawQuery().split("&")).stream()
                .map(part -> part.split("=", 2))
                .collect(Collectors.groupingBy(parts -> decode(parts[0]), Collectors.mapping(parts -> parts.length > 1 ? decode(parts[1]) : "", Collectors.toList())));
    }

    private static int parseInt(List<String> values, int fallback) {
        return values == null || values.isEmpty() ? fallback : Integer.parseInt(values.getFirst());
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
}
