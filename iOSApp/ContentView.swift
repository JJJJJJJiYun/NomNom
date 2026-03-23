import SwiftUI

struct ContentView: View {
    @State private var path: [AppRoute] = []
    @State private var viewModel = AppViewModel()

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(viewModel: viewModel) {
                Task {
                    await viewModel.startDecision()
                    if viewModel.currentMatchup != nil || viewModel.result != nil {
                        path = [.decision]
                    }
                }
            }
            .navigationDestination(for: AppRoute.self) { route in
                switch route {
                case .decision:
                    DecisionView(viewModel: viewModel) {
                        path.append(.result)
                    }
                case .result:
                    ResultView(viewModel: viewModel) {
                        viewModel.reset()
                        path = []
                        Task { await viewModel.loadRestaurants() }
                    }
                }
            }
            .alert("提示", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("好的", role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .task {
                if viewModel.restaurants.isEmpty {
                    await viewModel.loadRestaurants()
                }
            }
        }
    }
}

enum AppRoute: Hashable {
    case decision
    case result
}
