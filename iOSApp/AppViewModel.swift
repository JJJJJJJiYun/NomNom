import Foundation
import Observation

@MainActor
@Observable
final class AppViewModel {
    private(set) var baseURLText = BackendConfiguration.loadBaseURL()
    private(set) var connectionState: ConnectionState = .idle

    var context = DecisionContext.dinnerForTwo
    var restaurants: [RestaurantCardDTO] = []
    var sessionId: UUID?
    var currentMatchup: MatchupDTO?
    var result: DecisionResultDTO?
    var history: [DecisionHistoryItemDTO] = []
    var isLoading = false
    var errorMessage: String?

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

    func updateBaseURL(_ newValue: String) {
        let trimmed = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        baseURLText = trimmed
        BackendConfiguration.saveBaseURL(trimmed)
        resetSessionOnly()
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

    func loadRestaurants() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let client = try makeClient()
            let health = try await client.health()
            connectionState = .connected(service: health.service)
            let response = try await client.fetchRestaurants()
            restaurants = response.restaurants
            context = response.context.asDecisionContext()
        } catch BackendClientError.invalidBaseURL {
            connectionState = .failed
            errorMessage = "后端地址格式不正确，请在连接设置中修改"
        } catch {
            connectionState = .failed
            errorMessage = "无法连接后端，请先运行 backend/run.sh"
        }
    }

    func startDecision() async {
        guard !restaurants.isEmpty else {
            errorMessage = "后端尚未返回餐厅列表"
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            let response = try await makeClient().createDecision(candidateIds: restaurants.map(\.restaurant.id), context: context)
            sessionId = response.sessionId
            currentMatchup = response.nextMatchup
            result = response.result
            history = response.result?.history ?? []
        } catch {
            errorMessage = "创建决策失败，请确认后端已启动"
        }
    }

    func choose(_ outcome: DecisionOutcome) async {
        guard let sessionId, let currentMatchup else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let response = try await makeClient().vote(sessionId: sessionId, matchupId: currentMatchup.matchupId, winner: outcome)
            self.currentMatchup = response.nextMatchup
            self.result = response.result
            self.history = response.result?.history ?? history
        } catch {
            errorMessage = "提交选择失败，请稍后重试"
        }
    }

    func reloadAfterConfigChange() async {
        restaurants = []
        resetSessionOnly()
        await loadRestaurants()
    }

    func reset() {
        resetSessionOnly()
        errorMessage = nil
    }

    private func resetSessionOnly() {
        sessionId = nil
        currentMatchup = nil
        result = nil
        history = []
    }

    private func makeClient() throws -> BackendClient {
        try BackendClient(baseURLString: baseURLText)
    }
}
