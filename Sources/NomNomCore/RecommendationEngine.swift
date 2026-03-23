import Foundation

public struct RecommendationSnapshot: Codable, Equatable, Sendable {
    public let summary: String
    public let reasons: [String]
    public let pros: [String]
    public let cons: [String]

    public init(summary: String, reasons: [String], pros: [String], cons: [String]) {
        self.summary = summary
        self.reasons = reasons
        self.pros = pros
        self.cons = cons
    }
}

public enum RecommendationEngine {
    public static func snapshot(for restaurant: Restaurant, context: DecisionContext) -> RecommendationSnapshot {
        let summary = restaurant.headline
        var reasons: [String] = []

        if context.budgetRange.contains(restaurant.averagePrice) {
            reasons.append("人均 ¥\(restaurant.averagePrice)，符合你的预算")
        } else if restaurant.averagePrice < context.budgetRange.lowerBound {
            reasons.append("人均 ¥\(restaurant.averagePrice)，比预算更省")
        } else {
            reasons.append("评分高，但预算会略超")
        }

        if restaurant.distanceMeters <= context.maxDistanceMeters {
            reasons.append("距离你约 \(restaurant.distanceMeters)m")
        } else {
            reasons.append("口碑更强，但距离稍远")
        }

        if restaurant.isOpenNow {
            reasons.append("当前营业中，适合现在出发")
        } else {
            reasons.append("当前未营业，更适合收藏到稍后计划")
        }

        if context.preferredCuisines.contains(restaurant.cuisine) {
            reasons.append("菜系贴合你这次的选择偏好")
        }

        if context.peopleCount == 2, restaurant.reviewDigest.bestFor.contains(where: { $0.contains("双人") || $0.contains("约会") }) {
            reasons.append("评论普遍认为适合双人场景")
        }

        if restaurant.isFavorite {
            reasons.append("这家已经在你的收藏夹里")
        }

        let trimmedReasons = Array(reasons.prefix(4))
        return RecommendationSnapshot(
            summary: summary,
            reasons: trimmedReasons,
            pros: Array(restaurant.reviewDigest.pros.prefix(3)),
            cons: Array(restaurant.reviewDigest.cons.prefix(2))
        )
    }
}
