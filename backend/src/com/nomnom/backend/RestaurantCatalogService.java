package com.nomnom.backend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RestaurantCatalogService {
    private final List<Restaurant> restaurants = List.of(
            new Restaurant(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    "鸟居烧肉",
                    "日式烧肉",
                    138,
                    820,
                    4.6,
                    true,
                    true,
                    "静安寺",
                    "主打烧肉和下酒小食，适合下班后两人轻松吃一顿。",
                    List.of("约会", "日料", "氛围好"),
                    new ReviewDigest(List.of("双人晚餐", "约会"), List.of("牛舌口碑稳定", "环境有氛围", "服务响应快"), List.of("周末排队较久", "价格略高"))
            ),
            new Restaurant(
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    "汤城小厨",
                    "本帮菜",
                    92,
                    650,
                    4.3,
                    true,
                    false,
                    "静安寺",
                    "偏家常口味，出餐快，适合工作日晚饭或多人聚餐。",
                    List.of("家常菜", "聚餐", "出餐快"),
                    new ReviewDigest(List.of("工作日晚餐", "四人聚餐"), List.of("菜量足", "上菜快", "性价比稳"), List.of("环境偏嘈杂", "热门时段等位明显"))
            ),
            new Restaurant(
                    UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    "山葵割烹",
                    "割烹日料",
                    268,
                    1500,
                    4.8,
                    true,
                    true,
                    "南京西路",
                    "精致日料体验强，适合有预算的约会或庆祝场景。",
                    List.of("高端", "约会", "安静"),
                    new ReviewDigest(List.of("纪念日", "正式约会"), List.of("出品精细", "环境安静", "服务细致"), List.of("预算压力大", "位置稍远"))
            ),
            new Restaurant(
                    UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    "炭吉居酒屋",
                    "居酒屋",
                    118,
                    430,
                    4.4,
                    false,
                    true,
                    "常熟路",
                    "适合夜宵和小酌，串烧稳定，但开门时间偏晚。",
                    List.of("夜宵", "小酌", "串烧"),
                    new ReviewDigest(List.of("夜宵", "朋友小聚"), List.of("串烧稳定", "酒单丰富", "气氛轻松"), List.of("当前未营业", "座位偏紧凑"))
            )
    );

    public List<Restaurant> search(DecisionContext context) {
        return restaurants.stream()
                .filter(r -> r.distanceMeters() <= context.maxDistanceMeters())
                .sorted(Comparator.comparingDouble((Restaurant r) -> score(r, context)).reversed())
                .toList();
    }

    public Optional<Restaurant> findById(UUID id) {
        return restaurants.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public RestaurantCard toCard(Restaurant restaurant, DecisionContext context) {
        return new RestaurantCard(restaurant, buildSnapshot(restaurant, context));
    }

    public RecommendationSnapshot buildSnapshot(Restaurant restaurant, DecisionContext context) {
        List<String> reasons = new ArrayList<>();
        if (restaurant.averagePrice() >= context.budgetMin() && restaurant.averagePrice() <= context.budgetMax()) {
            reasons.add("人均 ¥%d，符合你的预算".formatted(restaurant.averagePrice()));
        } else if (restaurant.averagePrice() < context.budgetMin()) {
            reasons.add("人均 ¥%d，比预算更省".formatted(restaurant.averagePrice()));
        } else {
            reasons.add("评分高，但预算会略超");
        }
        if (restaurant.distanceMeters() <= context.maxDistanceMeters()) {
            reasons.add("距离你约 %dm".formatted(restaurant.distanceMeters()));
        } else {
            reasons.add("口碑更强，但距离稍远");
        }
        reasons.add(restaurant.openNow() ? "当前营业中，适合现在出发" : "当前未营业，更适合收藏到稍后计划");
        if (context.preferredCuisines().contains(restaurant.cuisine())) {
            reasons.add("菜系贴合你这次的选择偏好");
        }
        if (context.peopleCount() == 2 && restaurant.reviewDigest().bestFor().stream().anyMatch(tag -> tag.contains("双人") || tag.contains("约会"))) {
            reasons.add("评论普遍认为适合双人场景");
        }
        if (restaurant.favorite()) {
            reasons.add("这家已经在你的收藏夹里");
        }
        return new RecommendationSnapshot(
                restaurant.headline(),
                reasons.stream().limit(4).toList(),
                restaurant.reviewDigest().pros().stream().limit(3).toList(),
                restaurant.reviewDigest().cons().stream().limit(2).toList()
        );
    }

    private double score(Restaurant restaurant, DecisionContext context) {
        double budgetScore = restaurant.averagePrice() >= context.budgetMin() && restaurant.averagePrice() <= context.budgetMax() ? 1.0 : 0.4;
        double distanceScore = Math.max(0, 1 - (double) restaurant.distanceMeters() / Math.max(context.maxDistanceMeters(), 1));
        double favoriteScore = restaurant.favorite() ? 0.25 : 0.0;
        double cuisineScore = context.preferredCuisines().contains(restaurant.cuisine()) ? 0.3 : 0.0;
        double openScore = restaurant.openNow() ? 0.2 : -0.1;
        return restaurant.rating() + budgetScore + distanceScore + favoriteScore + cuisineScore + openScore;
    }
}
