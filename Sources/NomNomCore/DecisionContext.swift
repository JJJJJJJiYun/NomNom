import Foundation

public struct DecisionContext: Codable, Equatable, Sendable {
    public let budgetRange: ClosedRange<Int>
    public let preferredCuisines: [String]
    public let peopleCount: Int
    public let maxDistanceMeters: Int

    public init(
        budgetRange: ClosedRange<Int>,
        preferredCuisines: [String],
        peopleCount: Int,
        maxDistanceMeters: Int
    ) {
        self.budgetRange = budgetRange
        self.preferredCuisines = preferredCuisines
        self.peopleCount = peopleCount
        self.maxDistanceMeters = maxDistanceMeters
    }

    public static let dinnerForTwo = DecisionContext(
        budgetRange: 80...180,
        preferredCuisines: ["日式烧肉", "居酒屋", "割烹日料"],
        peopleCount: 2,
        maxDistanceMeters: 1500
    )
}
