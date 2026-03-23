import SwiftUI

struct ResultView: View {
    let viewModel: AppViewModel
    let restart: () -> Void

    var body: some View {
        ScrollView {
            if let result = viewModel.result {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("今晚就去这家")
                            .font(.headline)
                            .foregroundStyle(.secondary)
                        Text(result.winner.restaurant.name)
                            .font(.largeTitle.bold())
                        Text("\(result.winner.restaurant.cuisine) · \(result.winner.restaurant.neighborhood)")
                            .foregroundStyle(.secondary)
                    }

                    GroupBox("为什么它胜出") {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(result.winner.snapshot.reasons, id: \.self) { reason in
                                Text("• \(reason)")
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }

                    GroupBox("AI 简介") {
                        Text(result.winner.snapshot.summary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    GroupBox("优缺点摘要") {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("👍 \(result.winner.snapshot.pros.joined(separator: "、"))")
                            Text("⚠️ \(result.winner.snapshot.cons.joined(separator: "、"))")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    GroupBox("本轮对战记录") {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(result.history) { record in
                                Text("• \(record.leftRestaurantName) vs \(record.rightRestaurantName) → \(record.winnerRestaurantName)")
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }

                    Button(action: restart) {
                        Text("重新开始一轮")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(20)
            }
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("决策结果")
    }
}
