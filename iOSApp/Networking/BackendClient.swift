import Foundation

struct BackendClient {
    let baseURL: URL

    init(baseURLString: String) throws {
        guard let url = URL(string: baseURLString) else {
            throw BackendClientError.invalidBaseURL
        }
        self.baseURL = url
    }

    func health() async throws -> HealthResponseDTO {
        let (data, response) = try await URLSession.shared.data(from: baseURL.appending(path: "/api/v1/health"))
        try validate(response)
        return try JSONDecoder().decode(HealthResponseDTO.self, from: data)
    }

    func fetchRestaurants() async throws -> SearchResponseDTO {
        var components = URLComponents(url: baseURL.appending(path: "/api/v1/restaurants"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "budgetMin", value: "80"),
            URLQueryItem(name: "budgetMax", value: "180"),
            URLQueryItem(name: "peopleCount", value: "2"),
            URLQueryItem(name: "maxDistanceMeters", value: "1500")
        ]
        let (data, response) = try await URLSession.shared.data(from: components.url!)
        try validate(response)
        return try JSONDecoder().decode(SearchResponseDTO.self, from: data)
    }

    func createDecision(candidateIds: [UUID], context: DecisionContext) async throws -> DecisionResponseDTO {
        let payload = CreateDecisionRequestDTO(
            budgetMin: context.budgetRange.lowerBound,
            budgetMax: context.budgetRange.upperBound,
            preferredCuisines: context.preferredCuisines,
            peopleCount: context.peopleCount,
            maxDistanceMeters: context.maxDistanceMeters,
            candidateIds: candidateIds
        )
        return try await send(
            path: "/api/v1/decisions",
            method: "POST",
            body: payload,
            responseType: DecisionResponseDTO.self
        )
    }

    func vote(sessionId: UUID, matchupId: UUID, winner: DecisionOutcome) async throws -> DecisionResponseDTO {
        try await send(
            path: "/api/v1/decisions/\(sessionId.uuidString)/vote",
            method: "POST",
            body: VoteRequestDTO(matchupId: matchupId, winner: winner == .left ? "LEFT" : "RIGHT"),
            responseType: DecisionResponseDTO.self
        )
    }

    private func send<Request: Encodable, Response: Decodable>(path: String, method: String, body: Request, responseType: Response.Type) async throws -> Response {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response)
        return try JSONDecoder().decode(Response.self, from: data)
    }

    private func validate(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw BackendClientError.badServerResponse
        }
    }
}

enum BackendClientError: Error {
    case invalidBaseURL
    case badServerResponse
}
