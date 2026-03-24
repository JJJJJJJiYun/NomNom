import Foundation

struct MVPBackendClient {
    let baseURL: URL
    let decoder: JSONDecoder
    let encoder: JSONEncoder

    private static let fractionalSecondFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let internetDateTimeFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    init(baseURLString: String) throws {
        guard let url = URL(string: baseURLString) else {
            throw BackendClientError.invalidBaseURL
        }
        self.baseURL = url
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
        self.decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let rawValue = try container.decode(String.self)
            if let date = Self.fractionalSecondFormatter.date(from: rawValue) ??
                Self.internetDateTimeFormatter.date(from: rawValue) {
                return date
            }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unsupported ISO8601 date: \(rawValue)"
            )
        }
        self.encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(Self.fractionalSecondFormatter.string(from: date))
        }
    }

    func health() async throws -> HealthResponseDTO {
        let request = URLRequest(url: baseURL.appending(path: "/api/v1/health"))
        return try await send(request: request, responseType: HealthResponseDTO.self)
    }

    func registerDevice(deviceName: String, appVersion: String) async throws -> DeviceAuthResponseDTO {
        try await send(
            path: "/api/v1/auth/device",
            method: "POST",
            token: nil,
            body: DeviceAuthRequestDTO(
                installationId: InstallationIDStore.loadInstallationID(),
                deviceName: deviceName,
                appVersion: appVersion
            ),
            responseType: DeviceAuthResponseDTO.self
        )
    }

    func fetchVenueFilters(token: String, request: VenueSearchRequestDTO) async throws -> VenueFilterMetadataDTO {
        var components = URLComponents(url: baseURL.appending(path: "/api/v1/venues/filters"), resolvingAgainstBaseURL: false)
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "cityCode", value: request.cityCode),
            URLQueryItem(name: "latitude", value: request.latitude.map { String($0) }),
            URLQueryItem(name: "longitude", value: request.longitude.map { String($0) }),
            URLQueryItem(name: "radiusMeters", value: request.radiusMeters.map { String($0) }),
            URLQueryItem(name: "district", value: request.district),
            URLQueryItem(name: "businessArea", value: request.businessArea)
        ]
        queryItems.append(contentsOf: (request.categories ?? []).map { URLQueryItem(name: "categories", value: $0) })
        components?.queryItems = queryItems
        guard let url = components?.url else {
            throw BackendClientError.invalidBaseURL
        }
        let request = URLRequest(url: url)
        return try await send(request: authorized(request, token: token), responseType: VenueFilterMetadataDTO.self)
    }

    func searchVenues(token: String, request: VenueSearchRequestDTO) async throws -> VenueSearchResponseDTO {
        try await send(
            path: "/api/v1/venues/search",
            method: "POST",
            token: token,
            body: request,
            responseType: VenueSearchResponseDTO.self
        )
    }

    func importSharedLinks(token: String, items: [ShareImportItemDTO]) async throws -> ShareImportResponseDTO {
        try await send(
            path: "/api/v1/imports/share-links",
            method: "POST",
            token: token,
            body: ShareImportRequestDTO(imports: items),
            responseType: ShareImportResponseDTO.self
        )
    }

    func completeImport(token: String, importJobId: UUID, request: CompleteImportRequestDTO) async throws -> ImportJobResultDTO {
        try await send(
            path: "/api/v1/imports/\(importJobId.uuidString)/complete",
            method: "POST",
            token: token,
            body: request,
            responseType: ImportJobResultDTO.self
        )
    }

    func fetchDefaultList(token: String) async throws -> DefaultListResponseDTO {
        let request = URLRequest(url: baseURL.appending(path: "/api/v1/lists/default"))
        return try await send(request: authorized(request, token: token), responseType: DefaultListResponseDTO.self)
    }

    func addListItem(token: String, listId: UUID, venueId: UUID, note: String?) async throws -> AddListItemResponseDTO {
        try await send(
            path: "/api/v1/lists/\(listId.uuidString)/items",
            method: "POST",
            token: token,
            body: AddListItemRequestDTO(venueId: venueId, note: note),
            responseType: AddListItemResponseDTO.self
        )
    }

    func createDecisionSession(token: String, request: CreateDecisionSessionRequestDTO) async throws -> DecisionSessionResponseDTO {
        try await send(
            path: "/api/v1/decision-sessions",
            method: "POST",
            token: token,
            body: request,
            responseType: DecisionSessionResponseDTO.self
        )
    }

    func fetchDecisionSession(token: String, sessionId: UUID) async throws -> DecisionSessionResponseDTO {
        let request = URLRequest(url: baseURL.appending(path: "/api/v1/decision-sessions/\(sessionId.uuidString)"))
        return try await send(request: authorized(request, token: token), responseType: DecisionSessionResponseDTO.self)
    }

    func voteDecision(token: String, sessionId: UUID, matchupId: UUID, winnerVenueId: UUID) async throws -> DecisionSessionResponseDTO {
        try await send(
            path: "/api/v1/decision-sessions/\(sessionId.uuidString)/votes",
            method: "POST",
            token: token,
            body: VoteDecisionRequestDTO(matchupId: matchupId, winnerVenueId: winnerVenueId),
            responseType: DecisionSessionResponseDTO.self
        )
    }

    func fetchDecisionResult(token: String, sessionId: UUID) async throws -> DecisionResultResponseDTO {
        let request = URLRequest(url: baseURL.appending(path: "/api/v1/decision-sessions/\(sessionId.uuidString)/result"))
        return try await send(request: authorized(request, token: token), responseType: DecisionResultResponseDTO.self)
    }

    private func send<Request: Encodable, Response: Decodable>(
        path: String,
        method: String,
        token: String?,
        body: Request,
        responseType: Response.Type
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(body)
        return try await send(request: authorized(request, token: token), responseType: responseType)
    }

    private func send<Response: Decodable>(request: URLRequest, responseType: Response.Type) async throws -> Response {
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response, data: data)
        return try decoder.decode(Response.self, from: data)
    }

    private func authorized(_ request: URLRequest, token: String?) -> URLRequest {
        var mutable = request
        if let token, !token.isEmpty {
            mutable.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return mutable
    }

    private func validate(_ response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse else {
            throw BackendClientError.badServerResponse
        }
        guard (200...299).contains(http.statusCode) else {
            throw BackendClientError.badServerResponse
        }
    }
}

enum InstallationIDStore {
    private static let key = "nomnom.installationId"

    static func loadInstallationID() -> UUID {
        if let raw = UserDefaults.standard.string(forKey: key), let uuid = UUID(uuidString: raw) {
            return uuid
        }
        let newValue = UUID()
        UserDefaults.standard.set(newValue.uuidString, forKey: key)
        return newValue
    }
}
