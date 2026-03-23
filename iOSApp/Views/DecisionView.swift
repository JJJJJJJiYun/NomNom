import SwiftUI

struct DecisionView: View {
    let viewModel: AppViewModel
    let onFinished: () -> Void

    var body: some View {
        Group {
            if let matchup = viewModel.currentMatchup {
                ScrollView {
                    VStack(spacing: 18) {
                        Text("选出今晚最想去的一家")
                            .font(.title2.bold())
                            .frame(maxWidth: .infinity, alignment: .leading)

                        RestaurantDecisionCard(title: "左边", card: matchup.left) {
                            Task {
                                await viewModel.choose(.left)
                                if viewModel.result != nil { onFinished() }
                            }
                        }

                        RestaurantDecisionCard(title: "右边", card: matchup.right) {
                            Task {
                                await viewModel.choose(.right)
                                if viewModel.result != nil { onFinished() }
                            }
                        }

                        if !viewModel.history.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("已做出的选择")
                                    .font(.headline)
                                ForEach(viewModel.history) { record in
                                    Text("• \(record.leftRestaurantName) vs \(record.rightRestaurantName) → 你选了 \(record.winnerRestaurantName)")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    .padding(20)
                }
                .background(Color(.systemGroupedBackground))
            } else if viewModel.isLoading {
                ProgressView("等待后端返回下一轮对战…")
            } else {
                ProgressView("等待决策数据…")
            }
        }
        .navigationTitle("两两 PK")
    }
}

private struct RestaurantDecisionCard: View {
    let title: String
    let card: RestaurantCardDTO
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(title)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(card.restaurant.name)
                        .font(.title3.bold())
                    Text("\(card.restaurant.cuisine) · \(card.restaurant.neighborhood)")
                        .foregroundStyle(.secondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 6) {
                    Label("¥\(card.restaurant.averagePrice)", systemImage: "yensign.circle")
                    Label("\(card.restaurant.distanceMeters)m", systemImage: "location")
                    Label(String(format: "%.1f", card.restaurant.rating), systemImage: "star.fill")
                        .foregroundStyle(.orange)
                }
                .font(.subheadline)
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("推荐理由")
                    .font(.headline)
                FlowLayout(items: card.snapshot.reasons)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("AI 一句话简介")
                    .font(.headline)
                Text(card.snapshot.summary)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("评论摘要")
                    .font(.headline)
                summaryRow(title: "大家常夸", icon: "hand.thumbsup.fill", items: card.snapshot.pros, tint: .green)
                summaryRow(title: "常见提醒", icon: "exclamationmark.triangle.fill", items: card.snapshot.cons, tint: .orange)
            }

            Button(action: action) {
                if card.restaurant.isOpenNow {
                    Text("选这家")
                        .frame(maxWidth: .infinity)
                } else {
                    Text("先收藏，还是选这家")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func summaryRow(title: String, icon: String, items: [String], tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(title, systemImage: icon)
                .foregroundStyle(tint)
                .font(.subheadline.bold())
            ForEach(items, id: \.self) { item in
                Text("• \(item)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
