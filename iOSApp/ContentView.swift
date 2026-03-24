import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = AppViewModel()
    @State private var path: [AppRoute] = []
    @State private var hasBootstrapped = false

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(viewModel: viewModel) {
                Task { await startDecisionFlow() }
            } quickStartAction: {
                Task { await quickStartFlow() }
            }
            .navigationDestination(for: AppRoute.self) { route in
                switch route {
                case .decision:
                    DecisionView(viewModel: viewModel) {
                        path = [.result]
                    }
                case .result:
                    ResultView(viewModel: viewModel) {
                        viewModel.resetDecisionFlow()
                        path = []
                    }
                }
            }
            .task {
                guard !hasBootstrapped else { return }
                hasBootstrapped = true
                await viewModel.bootstrap()
            }
            .alert("提示", isPresented: errorPresented) {
                Button("知道了") {
                    viewModel.errorMessage = nil
                }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
        }
    }

    private var errorPresented: Binding<Bool> {
        Binding(
            get: { viewModel.errorMessage != nil },
            set: { newValue in
                if !newValue {
                    viewModel.errorMessage = nil
                }
            }
        )
    }

    private func startDecisionFlow() async {
        await viewModel.startDecision()
        if viewModel.result != nil {
            path = [.result]
        } else if viewModel.currentMatchup != nil {
            path = [.decision]
        }
    }

    private func quickStartFlow() async {
        await viewModel.startDecisionFromSearchResults()
        if viewModel.result != nil {
            path = [.result]
        } else if viewModel.currentMatchup != nil {
            path = [.decision]
        }
    }
}

enum AppRoute: Hashable {
    case decision
    case result
}
