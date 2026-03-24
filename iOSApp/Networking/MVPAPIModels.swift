import Foundation

struct DeviceAuthRequestDTO: Encodable {
    let installationId: UUID
    let deviceName: String
    let appVersion: String
}

struct DeviceAuthResponseDTO: Decodable {
    let userId: UUID
    let accessToken: String
    let expiresAt: Date
}

struct VenueFilterMetadataDTO: Decodable {
    let cityCode: String
    let districts: [VenueDistrictOptionDTO]
    let categories: [String]
    let sortOptions: [VenueSortOptionDTO]
    let priceRange: VenueNumericRangeDTO
    let ratingOptions: [Double]
    let radiusOptions: [Int]
    let peopleCountOptions: [Int]
}

struct VenueDistrictOptionDTO: Decodable, Identifiable {
    let name: String
    let businessAreas: [String]

    var id: String { name }
}

struct VenueSortOptionDTO: Decodable, Identifiable {
    let value: String
    let label: String

    var id: String { value }
}

struct VenueNumericRangeDTO: Decodable {
    let min: Int
    let max: Int
    let step: Int
}

struct VenueSearchRequestDTO: Encodable {
    let cityCode: String?
    let latitude: Double?
    let longitude: Double?
    let radiusMeters: Int?
    let district: String?
    let businessArea: String?
    let categories: [String]?
    let priceMin: Int?
    let priceMax: Int?
    let ratingMin: Double?
    let openNow: Bool?
    let sortBy: String?
    let page: Int?
    let pageSize: Int?
}

struct VenueSearchResponseDTO: Decodable {
    let items: [VenueSearchItemDTO]
    let page: Int
    let pageSize: Int
    let hasMore: Bool
    let searchStrategy: String?
    let notice: String?
}

struct VenueSearchItemDTO: Decodable, Identifiable {
    let venueId: UUID
    let name: String
    let coverImageUrl: String?
    let category: String
    let subcategory: String
    let avgPrice: Int
    let rating: Double
    let reviewCount: Int
    let distanceMeters: Int?
    let openStatus: String
    let district: String
    let businessArea: String
    let address: String
    let sourceProvider: String
    let sourceUrl: String?
    let isInFavorites: Bool

    var id: UUID { venueId }
}

struct ShareImportRequestDTO: Encodable {
    let imports: [ShareImportItemDTO]
}

struct ShareImportItemDTO: Encodable {
    let sourceProvider: String
    let sharedText: String?
    let sharedUrl: String?
}

struct ShareImportResponseDTO: Decodable {
    let results: [ImportJobResultDTO]
}

struct ImportJobResultDTO: Decodable, Identifiable {
    let importJobId: UUID
    let status: String
    let venueId: UUID?
    let requiresManualCompletion: Bool

    var id: UUID { importJobId }
}

struct CompleteImportRequestDTO: Encodable {
    let name: String
    let category: String
    let avgPrice: Int
    let district: String
    let businessArea: String
    let address: String
    let tags: [String]
}

struct DefaultListResponseDTO: Decodable {
    let listId: UUID
    let name: String
    let items: [DefaultListItemDTO]
}

struct DefaultListItemDTO: Decodable, Identifiable {
    let itemId: UUID
    let venueId: UUID
    let name: String
    let avgPrice: Int
    let rating: Double
    let distanceMeters: Int?
    let note: String?
    let sourceProvider: String?

    var id: UUID { itemId }
}

struct AddListItemRequestDTO: Encodable {
    let venueId: UUID
    let note: String?
}

struct AddListItemResponseDTO: Decodable {
    let itemId: UUID
}

struct CreateDecisionSessionRequestDTO: Encodable {
    let name: String
    let context: DecisionSessionContextDTO
    let candidateVenueIds: [UUID]
}

struct DecisionSessionContextDTO: Codable {
    let peopleCount: Int?
    let priceMin: Int?
    let priceMax: Int?
    let openNow: Bool?
    let district: String?
    let businessArea: String?
}

struct DecisionSessionResponseDTO: Decodable {
    let sessionId: UUID
    let status: String
    let round: Int
    let context: DecisionSessionContextDTO
    let history: [DecisionMatchHistoryDTO]
    let nextMatchup: DecisionMatchupDTO?
}

struct DecisionMatchHistoryDTO: Decodable, Identifiable {
    let matchupId: UUID
    let round: Int
    let leftVenueId: UUID
    let rightVenueId: UUID
    let winnerVenueId: UUID
    let decidedAt: Date?

    var id: UUID { matchupId }
}

struct DecisionMatchupDTO: Decodable {
    let matchupId: UUID
    let left: DecisionVenueSummaryDTO
    let right: DecisionVenueSummaryDTO
}

struct DecisionVenueSummaryDTO: Decodable {
    let venueId: UUID
    let name: String
    let avgPrice: Int
    let rating: Double
    let openStatus: String
}

struct VoteDecisionRequestDTO: Encodable {
    let matchupId: UUID
    let winnerVenueId: UUID
}

struct DecisionResultResponseDTO: Decodable {
    let sessionId: UUID
    let status: String
    let winner: DecisionVenueSummaryDTO?
    let reasons: [String]
    let history: [DecisionMatchHistoryDTO]
}
