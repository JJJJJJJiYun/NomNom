import Foundation

struct HealthResponseDTO: Codable {
    let status: String
    let service: String
}

struct RestaurantCardDTO: Codable, Identifiable, Hashable {
    let restaurant: Restaurant
    let snapshot: RecommendationSnapshot

    var id: UUID { restaurant.id }
}

struct SearchResponseDTO: Codable {
    let context: BackendDecisionContextDTO
    let restaurants: [RestaurantCardDTO]
}

struct BackendDecisionContextDTO: Codable {
    let budgetMin: Int
    let budgetMax: Int
    let preferredCuisines: [String]
    let peopleCount: Int
    let maxDistanceMeters: Int

    func asDecisionContext() -> DecisionContext {
        DecisionContext(
            budgetRange: budgetMin...budgetMax,
            preferredCuisines: preferredCuisines,
            peopleCount: peopleCount,
            maxDistanceMeters: maxDistanceMeters
        )
    }
}

struct CreateDecisionRequestDTO: Encodable {
    let budgetMin: Int
    let budgetMax: Int
    let preferredCuisines: [String]
    let peopleCount: Int
    let maxDistanceMeters: Int
    let candidateIds: [UUID]
}

struct VoteRequestDTO: Encodable {
    let matchupId: UUID
    let winner: String
}

struct DecisionResponseDTO: Codable {
    let sessionId: UUID
    let context: BackendDecisionContextDTO
    let status: String
    let nextMatchup: MatchupDTO?
    let result: DecisionResultDTO?
}

struct MatchupDTO: Codable, Hashable {
    let matchupId: UUID
    let left: RestaurantCardDTO
    let right: RestaurantCardDTO
}

struct DecisionResultDTO: Codable {
    let winner: RestaurantCardDTO
    let history: [DecisionHistoryItemDTO]
}

struct DecisionHistoryItemDTO: Codable, Identifiable {
    let id: UUID
    let leftRestaurantName: String
    let rightRestaurantName: String
    let winnerRestaurantName: String
}
