import Foundation
import Combine

@MainActor
final class AppViewModel: ObservableObject {
    private let autoCandidateLimit = 6

    @Published private(set) var baseURLText = BackendConfiguration.loadBaseURL()
    @Published private(set) var connectionState: ConnectionState = .idle
    @Published private(set) var filterMetadata: VenueFilterMetadataDTO?
    @Published private(set) var searchStrategy: String?
    @Published private(set) var searchNotice: String?

    @Published var filters = SearchFilters()
    @Published var shareImportText = ""
    @Published var searchResults: [VenueSearchItemDTO] = []
    @Published var favorites: [DefaultListItemDTO] = []
    @Published var candidateVenueIds: [UUID] = []
    @Published var currentMatchup: DecisionMatchupDTO?
    @Published var result: DecisionResultResponseDTO?
    @Published var history: [DecisionMatchHistoryDTO] = []
    @Published var isLoading = false
    @Published var isVoting = false
    @Published var isImporting = false
    @Published var errorMessage: String?
    @Published var importDraft = ManualImportDraft()
    @Published var pendingImportJobId: UUID?
    @Published var showingImportCompletionSheet = false

    private var userId: UUID?
    private var accessToken: String?
    private var favoritesListId: UUID?
    private var sessionId: UUID?
    private var venueLookup: [UUID: CandidateVenue] = [:]

    enum ConnectionState: Equatable {
        case idle
        case checking
        case connected(service: String)
        case failed
    }

    var connectionLabel: String {
        switch connectionState {
        case .idle:
            return "未检测"
        case .checking:
            return "检测中"
        case let .connected(service):
            return "已连接 · \(service)"
        case .failed:
            return "连接失败"
        }
    }

    var selectedCandidates: [CandidateVenue] {
        candidateVenueIds.compactMap { venueLookup[$0] }
    }

    var hasActiveSession: Bool {
        currentMatchup != nil || result != nil || sessionId != nil
    }

    var canStartDecision: Bool {
        candidateVenueIds.count >= 2 && !isLoading && !isVoting
    }

    var canFillCandidatesFromSearchResults: Bool {
        searchResults.count >= 2 && !isLoading && !isVoting
    }

    var canStartFromSearchResults: Bool {
        searchResults.count >= 2 && !isLoading && !isVoting
    }

    var suggestedCandidateCount: Int {
        min(searchResults.count, autoCandidateLimit)
    }

    var searchSelectionHint: String? {
        guard suggestedCandidateCount >= 2 else { return nil }
        let recommendedIds = Set(searchResults.prefix(autoCandidateLimit).map(\.venueId))
        let selectedRecommendedCount = candidateVenueIds.filter { recommendedIds.contains($0) }.count
        return "搜索后会默认把前 \(suggestedCandidateCount) 家推荐加入 PK 候选，当前保留了 \(selectedRecommendedCount) 家。"
    }

    var districtOptions: [String] {
        filterMetadata?.districts.map(\.name) ?? []
    }

    var businessAreaOptions: [String] {
        guard let metadata = filterMetadata else { return [] }
        return businessAreas(from: metadata, district: filters.district)
    }

    var categoryOptions: [String] {
        filterMetadata?.categories ?? []
    }

    var sortOptions: [VenueSortOptionDTO] {
        filterMetadata?.sortOptions ?? SearchFilters.fallbackSortOptions
    }

    var radiusOptions: [Int] {
        filterMetadata?.radiusOptions ?? SearchFilters.fallbackRadiusOptions
    }

    var ratingOptions: [Double] {
        filterMetadata?.ratingOptions ?? SearchFilters.fallbackRatingOptions
    }

    var peopleCountOptions: [Int] {
        filterMetadata?.peopleCountOptions ?? SearchFilters.fallbackPeopleCountOptions
    }

    var priceRange: VenueNumericRangeDTO {
        filterMetadata?.priceRange ?? VenueNumericRangeDTO(min: 0, max: 500, step: 10)
    }

    var selectedDistrictLabel: String {
        filters.district ?? "不限"
    }

    var selectedBusinessAreaLabel: String {
        filters.businessArea ?? "不限"
    }

    var selectedSortLabel: String {
        sortOptions.first(where: { $0.value == filters.sortBy })?.label ?? "推荐排序"
    }

    var searchStrategyLabel: String? {
        switch searchStrategy {
        case "REMOTE_AMAP":
            return "当前搜索：高德地图"
        case "REMOTE_TENCENT":
            return "当前搜索：腾讯位置服务"
        case "REMOTE_BAIDU":
            return "当前搜索：百度地图"
        case "LOCAL_CACHE":
            return "当前搜索：本地缓存/样例"
        default:
            return nil
        }
    }

    func updateBaseURL(_ newValue: String) {
        let trimmed = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        baseURLText = trimmed
        BackendConfiguration.saveBaseURL(trimmed)
        accessToken = nil
        userId = nil
        favoritesListId = nil
        filterMetadata = nil
        searchStrategy = nil
        searchNotice = nil
        searchResults = []
        favorites = []
        candidateVenueIds = []
        venueLookup = [:]
        resetSessionOnly()
    }

    func bootstrap() async {
        await checkBackendConnection()
        guard connectionState != .failed else { return }
        await reloadHome()
    }

    func checkBackendConnection() async {
        connectionState = .checking
        do {
            let health = try await makeClient().health()
            connectionState = .connected(service: health.service)
        } catch {
            connectionState = .failed
            errorMessage = "无法连接后端，请确认 \(baseURLText) 可访问"
        }
    }

    func reloadHome() async {
        isLoading = true
        defer { isLoading = false }
        errorMessage = nil
        do {
            let client = try makeClient()
            let token = try await ensureAuthenticated(client: client)
            let health = try await client.health()
            connectionState = .connected(service: health.service)
            let currentRequest = filters.asRequest()
            let metadata = try await client.fetchVenueFilters(token: token, request: currentRequest)
            applyFilterMetadata(metadata)
            async let searchTask = client.searchVenues(token: token, request: currentRequest)
            async let favoritesTask = client.fetchDefaultList(token: token)
            let (searchResponse, favoritesResponse) = try await (searchTask, favoritesTask)
            applySearchResults(searchResponse)
            applyFavorites(favoritesResponse)
        } catch BackendClientError.invalidBaseURL {
            connectionState = .failed
            errorMessage = "后端地址格式不正确，请在连接设置中修改"
        } catch {
            connectionState = .failed
            errorMessage = "刷新数据失败，请确认后端已启动并已注册设备"
        }
    }

    func startDecision() async {
        guard canStartDecision else {
            errorMessage = "请先至少选择 2 家候选餐厅"
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            let client = try makeClient()
            let token = try await ensureAuthenticated(client: client)
            let response = try await client.createDecisionSession(
                token: token,
                request: CreateDecisionSessionRequestDTO(
                    name: "今晚吃什么",
                    context: filters.asDecisionContext(),
                    candidateVenueIds: candidateVenueIds
                )
            )
            sessionId = response.sessionId
            currentMatchup = response.nextMatchup
            result = nil
            history = response.history
            cache(response.nextMatchup)
            if response.status == "COMPLETED" {
                result = try await client.fetchDecisionResult(token: token, sessionId: response.sessionId)
            }
        } catch {
            errorMessage = "创建决策失败，请确认后端已启动"
        }
    }

    func choose(_ winnerVenueId: UUID) async {
        guard let sessionId, let currentMatchup, !isVoting else { return }
        isVoting = true
        defer { isVoting = false }
        do {
            let client = try makeClient()
            let token = try await ensureAuthenticated(client: client)
            let response = try await client.voteDecision(
                token: token,
                sessionId: sessionId,
                matchupId: currentMatchup.matchupId,
                winnerVenueId: winnerVenueId
            )
            self.currentMatchup = response.nextMatchup
            self.history = response.history
            cache(response.nextMatchup)
            if response.status == "COMPLETED" {
                self.result = try await client.fetchDecisionResult(token: token, sessionId: sessionId)
            }
        } catch {
            errorMessage = "提交选择失败，请稍后重试"
        }
    }

    func toggleCandidate(_ venueId: UUID) {
        if let index = candidateVenueIds.firstIndex(of: venueId) {
            candidateVenueIds.remove(at: index)
        } else if candidateVenueIds.count < 32 {
            candidateVenueIds.append(venueId)
        } else {
            errorMessage = "候选池最多支持 32 家"
        }
    }

    func fillCandidatesFromSearchResults(limit: Int? = nil) {
        let candidateLimit = limit ?? autoCandidateLimit
        let ids = Array(searchResults.prefix(candidateLimit).map(\.venueId))
        guard ids.count >= 2 else {
            errorMessage = "当前搜索结果不足 2 家，先调整筛选条件"
            return
        }
        candidateVenueIds = ids
    }

    func startDecisionFromSearchResults() async {
        if searchResults.count < 2 {
            await reloadHome()
        }
        guard searchResults.count >= 2 else {
            errorMessage = "当前搜索结果不足 2 家，先调整筛选条件"
            return
        }
        if candidateVenueIds.count < 2 {
            fillCandidatesFromSearchResults()
        }
        await startDecision()
    }

    func selectDistrict(_ district: String?) {
        filters.district = normalizedOptional(district)
        if let selectedBusinessArea = filters.businessArea,
           !businessAreaOptions.contains(selectedBusinessArea) {
            filters.businessArea = nil
        }
    }

    func selectBusinessArea(_ businessArea: String?) {
        filters.businessArea = normalizedOptional(businessArea)
    }

    func toggleCategorySelection(_ category: String) {
        filters.toggleCategory(category)
    }

    func selectSort(_ sortValue: String) {
        filters.sortBy = sortValue
    }

    func selectRadius(_ radius: Int) {
        filters.radiusMeters = radius
    }

    func selectRating(_ rating: Double) {
        filters.ratingMin = rating
    }

    func selectPeopleCount(_ count: Int) {
        filters.peopleCount = count
    }

    func isSelected(_ venueId: UUID) -> Bool {
        candidateVenueIds.contains(venueId)
    }

    func candidateVenue(for venueId: UUID) -> CandidateVenue? {
        venueLookup[venueId]
    }

    func addToFavorites(_ venueId: UUID) async {
        guard let favoritesListId else {
            errorMessage = "收藏列表尚未就绪"
            return
        }
        do {
            let client = try makeClient()
            let token = try await ensureAuthenticated(client: client)
            _ = try await client.addListItem(token: token, listId: favoritesListId, venueId: venueId, note: nil)
            let response = try await client.fetchDefaultList(token: token)
            applyFavorites(response)
        } catch {
            errorMessage = "加入收藏失败，请稍后再试"
        }
    }

    func importSharedText() async {
        let trimmed = shareImportText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = "请先粘贴大众点评分享文本或链接"
            return
        }
        isImporting = true
        defer { isImporting = false }
        do {
            let client = try makeClient()
            let token = try await ensureAuthenticated(client: client)
            let response = try await client.importSharedLinks(
                token: token,
                items: [ShareImportItemDTO(sourceProvider: "DIANPING", sharedText: trimmed, sharedUrl: extractURL(from: trimmed))]
            )
            guard let first = response.results.first else {
                errorMessage = "导入失败，后端未返回结果"
                return
            }
            if first.requiresManualCompletion {
                pendingImportJobId = first.importJobId
                importDraft = ManualImportDraft()
                showingImportCompletionSheet = true
            } else {
                shareImportText = ""
                await reloadHome()
            }
        } catch {
            errorMessage = "导入失败，请检查分享文本或后端状态"
        }
    }

    func completePendingImport() async {
        guard let pendingImportJobId else { return }
        let draft = importDraft.normalized()
        guard draft.isValid else {
            errorMessage = "请至少补全店名、分类、价格和地址"
            return
        }
        isImporting = true
        defer { isImporting = false }
        do {
            let client = try makeClient()
            let token = try await ensureAuthenticated(client: client)
            _ = try await client.completeImport(
                token: token,
                importJobId: pendingImportJobId,
                request: CompleteImportRequestDTO(
                    name: draft.name,
                    category: draft.category,
                    avgPrice: draft.avgPrice,
                    district: draft.district,
                    businessArea: draft.businessArea,
                    address: draft.address,
                    tags: draft.tags
                )
            )
            self.pendingImportJobId = nil
            self.showingImportCompletionSheet = false
            self.shareImportText = ""
            await reloadHome()
        } catch {
            errorMessage = "补全导入失败，请稍后再试"
        }
    }

    func cancelImportCompletion() {
        pendingImportJobId = nil
        showingImportCompletionSheet = false
    }

    func resetDecisionFlow(keepCandidates: Bool = true) {
        resetSessionOnly()
        errorMessage = nil
        if !keepCandidates {
            candidateVenueIds = []
        }
    }

    private func resetSessionOnly() {
        sessionId = nil
        currentMatchup = nil
        result = nil
        history = []
    }

    private func makeClient() throws -> MVPBackendClient {
        try MVPBackendClient(baseURLString: baseURLText)
    }

    private func ensureAuthenticated(client: MVPBackendClient) async throws -> String {
        let response = try await client.registerDevice(
            deviceName: deviceName(),
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        )
        userId = response.userId
        accessToken = response.accessToken
        return response.accessToken
    }

    private func applySearchResults(_ response: VenueSearchResponseDTO) {
        searchStrategy = response.searchStrategy
        searchNotice = response.notice
        searchResults = response.items
        for item in response.items {
            venueLookup[item.venueId] = CandidateVenue(
                id: item.venueId,
                name: item.name,
                category: item.subcategory,
                subtitle: [item.district, item.businessArea].joined(separator: " · "),
                avgPrice: item.avgPrice,
                rating: item.rating,
                distanceMeters: item.distanceMeters,
                openStatus: item.openStatus,
                sourceProvider: sourceProviderLabel(for: item.sourceProvider),
                note: nil
            )
        }
        if !hasActiveSession {
            candidateVenueIds = Array(response.items.prefix(autoCandidateLimit).map(\.venueId))
        }
    }

    private func applyFilterMetadata(_ metadata: VenueFilterMetadataDTO) {
        filterMetadata = metadata
        filters.cityCode = metadata.cityCode

        if let district = filters.district, !districtOptions.contains(district) {
            filters.district = districtOptions.first
        }

        if let businessArea = filters.businessArea, !businessAreas(from: metadata, district: filters.district).contains(businessArea) {
            filters.businessArea = nil
        }

        let categorySet = Set(metadata.categories)
        filters.selectedCategories = filters.selectedCategories.filter { categorySet.contains($0) }
        if filters.selectedCategories.isEmpty {
            filters.selectedCategories = Array(metadata.categories.prefix(3))
        }

        if !metadata.sortOptions.contains(where: { $0.value == filters.sortBy }) {
            filters.sortBy = metadata.sortOptions.first?.value ?? "RECOMMENDED"
        }

        filters.priceMin = max(metadata.priceRange.min, min(filters.priceMin, metadata.priceRange.max))
        filters.priceMax = max(filters.priceMin, min(filters.priceMax, metadata.priceRange.max))

        if !metadata.radiusOptions.contains(filters.radiusMeters) {
            filters.radiusMeters = nearestIntOption(to: filters.radiusMeters, options: metadata.radiusOptions) ?? 3000
        }

        if !metadata.ratingOptions.contains(filters.ratingMin) {
            filters.ratingMin = nearestDoubleOption(to: filters.ratingMin, options: metadata.ratingOptions) ?? 4.0
        }

        if !metadata.peopleCountOptions.contains(filters.peopleCount) {
            filters.peopleCount = nearestIntOption(to: filters.peopleCount, options: metadata.peopleCountOptions) ?? 2
        }
    }

    private func applyFavorites(_ response: DefaultListResponseDTO) {
        favoritesListId = response.listId
        favorites = response.items
        for item in response.items {
            let existing = venueLookup[item.venueId]
            venueLookup[item.venueId] = CandidateVenue(
                id: item.venueId,
                name: item.name,
                category: existing?.category ?? "收藏餐厅",
                subtitle: existing?.subtitle ?? "来自我的收藏",
                avgPrice: item.avgPrice,
                rating: item.rating,
                distanceMeters: existing?.distanceMeters ?? item.distanceMeters,
                openStatus: existing?.openStatus ?? "UNKNOWN",
                sourceProvider: sourceProviderLabel(for: item.sourceProvider ?? existing?.sourceProvider ?? "NOMNOM"),
                note: item.note
            )
        }
    }

    private func cache(_ matchup: DecisionMatchupDTO?) {
        guard let matchup else { return }
        venueLookup[matchup.left.venueId] = CandidateVenue(
            id: matchup.left.venueId,
            name: matchup.left.name,
            category: venueLookup[matchup.left.venueId]?.category ?? "候选餐厅",
            subtitle: venueLookup[matchup.left.venueId]?.subtitle ?? "来自本轮对战",
            avgPrice: matchup.left.avgPrice,
            rating: matchup.left.rating,
            distanceMeters: venueLookup[matchup.left.venueId]?.distanceMeters,
            openStatus: matchup.left.openStatus,
            sourceProvider: venueLookup[matchup.left.venueId]?.sourceProvider ?? "NOMNOM",
            note: venueLookup[matchup.left.venueId]?.note
        )
        venueLookup[matchup.right.venueId] = CandidateVenue(
            id: matchup.right.venueId,
            name: matchup.right.name,
            category: venueLookup[matchup.right.venueId]?.category ?? "候选餐厅",
            subtitle: venueLookup[matchup.right.venueId]?.subtitle ?? "来自本轮对战",
            avgPrice: matchup.right.avgPrice,
            rating: matchup.right.rating,
            distanceMeters: venueLookup[matchup.right.venueId]?.distanceMeters,
            openStatus: matchup.right.openStatus,
            sourceProvider: venueLookup[matchup.right.venueId]?.sourceProvider ?? "NOMNOM",
            note: venueLookup[matchup.right.venueId]?.note
        )
    }

    private func deviceName() -> String {
        "NomNom iPhone"
    }

    private func extractURL(from text: String) -> String? {
        let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return detector?.firstMatch(in: text, options: [], range: range)?.url?.absoluteString
    }

    private func businessAreas(from metadata: VenueFilterMetadataDTO, district: String?) -> [String] {
        if let district,
           let match = metadata.districts.first(where: { $0.name == district }) {
            return match.businessAreas
        }
        return Array(Set(metadata.districts.flatMap(\.businessAreas))).sorted()
    }

    func sourceProviderLabel(for provider: String?) -> String {
        guard let provider else { return "NomNom" }
        return switch provider.uppercased() {
        case "AMAP_LBS":
            "高德地图"
        case "TENCENT_LBS":
            "腾讯位置服务"
        case "BAIDU_MAPS":
            "百度地图"
        case "LOCAL_SAMPLE":
            "样例数据"
        case "DIANPING":
            "大众点评"
        case "NOMNOM":
            "NomNom"
        default:
            provider
        }
    }

    private func normalizedOptional(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }

    private func nearestIntOption(to value: Int, options: [Int]) -> Int? {
        options.min(by: { abs($0 - value) < abs($1 - value) })
    }

    private func nearestDoubleOption(to value: Double, options: [Double]) -> Double? {
        options.min(by: { abs($0 - value) < abs($1 - value) })
    }
}

struct SearchFilters {
    var cityCode = "shanghai"
    var district: String? = "静安区"
    var businessArea: String?
    var selectedCategories = ["日料", "本帮菜", "法餐"]
    var priceMin = 90
    var priceMax = 270
    var radiusMeters = 3000
    var ratingMin = 4.0
    var openNow = true
    var sortBy = "RECOMMENDED"
    var peopleCount = 2

    static let fallbackSortOptions = [
        VenueSortOptionDTO(value: "RECOMMENDED", label: "推荐排序"),
        VenueSortOptionDTO(value: "DISTANCE", label: "距离最近"),
        VenueSortOptionDTO(value: "RATING", label: "评分最高"),
        VenueSortOptionDTO(value: "PRICE_ASC", label: "人均从低到高"),
        VenueSortOptionDTO(value: "PRICE_DESC", label: "人均从高到低")
    ]

    static let fallbackRadiusOptions = [1000, 2000, 3000, 5000, 8000]
    static let fallbackRatingOptions = [3.5, 4.0, 4.5]
    static let fallbackPeopleCountOptions = [1, 2, 4, 6, 8]

    mutating func toggleCategory(_ category: String) {
        if let index = selectedCategories.firstIndex(of: category) {
            selectedCategories.remove(at: index)
        } else {
            selectedCategories.append(category)
        }
    }

    func asRequest() -> VenueSearchRequestDTO {
        VenueSearchRequestDTO(
            cityCode: cityCode,
            latitude: 31.2281,
            longitude: 121.4547,
            radiusMeters: radiusMeters,
            district: district,
            businessArea: businessArea,
            categories: selectedCategories.isEmpty ? nil : selectedCategories,
            priceMin: priceMin,
            priceMax: priceMax,
            ratingMin: ratingMin,
            openNow: openNow,
            sortBy: sortBy,
            page: 1,
            pageSize: 20
        )
    }

    func asDecisionContext() -> DecisionSessionContextDTO {
        DecisionSessionContextDTO(
            peopleCount: peopleCount,
            priceMin: priceMin,
            priceMax: priceMax,
            openNow: openNow,
            district: district,
            businessArea: businessArea
        )
    }
}

struct CandidateVenue: Identifiable, Hashable {
    let id: UUID
    let name: String
    let category: String
    let subtitle: String
    let avgPrice: Int
    let rating: Double
    let distanceMeters: Int?
    let openStatus: String
    let sourceProvider: String
    let note: String?
}

struct ManualImportDraft {
    var name = ""
    var category = ""
    var avgPriceText = ""
    var district = ""
    var businessArea = ""
    var address = ""
    var tagsText = ""

    var avgPrice: Int {
        Int(avgPriceText) ?? 0
    }

    var tags: [String] {
        tagsText
            .split(whereSeparator: { ",，、".contains($0) })
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !category.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        avgPrice > 0 &&
        !district.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func normalized() -> ManualImportDraft {
        var copy = self
        copy.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        copy.category = category.trimmingCharacters(in: .whitespacesAndNewlines)
        copy.district = district.trimmingCharacters(in: .whitespacesAndNewlines)
        copy.businessArea = businessArea.trimmingCharacters(in: .whitespacesAndNewlines)
        copy.address = address.trimmingCharacters(in: .whitespacesAndNewlines)
        copy.tagsText = tagsText.trimmingCharacters(in: .whitespacesAndNewlines)
        return copy
    }
}
