import SwiftUI

struct HomeView: View {
    let viewModel: AppViewModel
    let startAction: () -> Void
    @State private var showingBackendSettings = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 12) {
                    Text("NomNom V1.5")
                        .font(.largeTitle.bold())
                    Text("前后端联调版：候选餐厅、推荐理由、AI 一句话简介、好评/差评摘要均由 Java 后端返回。")
                        .foregroundStyle(.secondary)
                }

                backendStatusCard
                contextCard

                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("本轮候选")
                            .font(.title2.bold())
                        Spacer()
                        if viewModel.isLoading {
                            ProgressView()
                        }
                    }
                    ForEach(viewModel.restaurants) { card in
                        RestaurantPreviewCard(card: card)
                    }
                }

                Button(action: startAction) {
                    Text("开始今晚的选择")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.restaurants.count < 2 || viewModel.isLoading)
            }
            .padding(20)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("决策助手")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("连接设置") {
                    showingBackendSettings = true
                }
            }
        }
        .sheet(isPresented: $showingBackendSettings) {
            BackendSettingsView(baseURL: viewModel.baseURLText) { newURL in
                viewModel.updateBaseURL(newURL)
                Task { await viewModel.reloadAfterConfigChange() }
            } onCheck: {
                Task { await viewModel.checkBackendConnection() }
            }
        }
    }

    private var backendStatusCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("后端连接", systemImage: "network")
                .font(.headline)
            Text(viewModel.baseURLText)
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)
            HStack(spacing: 8) {
                Circle()
                    .fill(connectionColor)
                    .frame(width: 10, height: 10)
                Text(viewModel.connectionLabel)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Text("Simulator 可直接使用 127.0.0.1；真机请改成你 Mac 的局域网 IP。")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var contextCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("晚饭双人场景", systemImage: "person.2.fill")
                .font(.headline)
            Text("预算 ¥\(viewModel.context.budgetRange.lowerBound)-¥\(viewModel.context.budgetRange.upperBound) · 最远 \(viewModel.context.maxDistanceMeters)m")
                .foregroundStyle(.secondary)
            Text("偏好：\(viewModel.context.preferredCuisines.joined(separator: "、"))")
                .foregroundStyle(.secondary)
            Text("数据来源：\(viewModel.baseURLText)/api/v1/restaurants")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var connectionColor: Color {
        switch viewModel.connectionState {
        case .idle:
            return .gray
        case .checking:
            return .orange
        case .connected:
            return .green
        case .failed:
            return .red
        }
    }
}

private struct RestaurantPreviewCard: View {
    let card: RestaurantCardDTO

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(card.restaurant.name)
                        .font(.headline)
                    Text("\(card.restaurant.cuisine) · ¥\(card.restaurant.averagePrice) · \(card.restaurant.distanceMeters)m")
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text(String(format: "%.1f", card.restaurant.rating))
                    .font(.title3.bold())
                    .foregroundStyle(.orange)
            }

            Text(card.snapshot.summary)
                .font(.subheadline)

            FlowLayout(items: Array(card.snapshot.reasons.prefix(3)))
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

private struct BackendSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draftURL: String
    let onSave: (String) -> Void
    let onCheck: () -> Void

    init(baseURL: String, onSave: @escaping (String) -> Void, onCheck: @escaping () -> Void) {
        _draftURL = State(initialValue: baseURL)
        self.onSave = onSave
        self.onCheck = onCheck
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("后端地址") {
                    TextField("http://127.0.0.1:8080", text: $draftURL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                    Text("Simulator 推荐：http://127.0.0.1:8080")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text("真机推荐：把 127.0.0.1 改成你 Mac 的局域网 IP，例如 http://192.168.1.10:8080")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("连接设置")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button("检测") {
                        onSave(draftURL)
                        onCheck()
                    }
                    Button("保存") {
                        onSave(draftURL)
                        dismiss()
                    }
                }
            }
        }
    }
}
