import SwiftUI

struct ResultView: View {
    @ObservedObject var viewModel: AppViewModel
    let restart: () -> Void

    var body: some View {
        ScrollView {
            if let result = viewModel.result, let winner = result.winner {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("今晚就去这家")
                            .font(.headline)
                            .foregroundStyle(.secondary)
                        Text(winner.name)
                            .font(.system(size: 34, weight: .bold, design: .rounded))
                        HStack(spacing: 14) {
                            Label("¥\(winner.avgPrice)", systemImage: "yensign.circle")
                            Label(String(format: "%.1f", winner.rating), systemImage: "star.fill")
                                .foregroundStyle(.orange)
                            Text(winner.openStatus == "OPEN" ? "营业中" : winner.openStatus)
                                .foregroundStyle(.secondary)
                        }
                    }

                    GroupBox("为什么它胜出") {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(result.reasons, id: \.self) { reason in
                                Text("• \(reason)")
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    GroupBox("本轮对战记录") {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(result.history) { record in
                                Text("第 \(record.round) 轮 · 胜者 \(winnerLabel(for: record.winnerVenueId))")
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    Button(action: restart) {
                        Text("回到候选池，重新开始")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(20)
            }
        }
        .background(
            LinearGradient(
                colors: [Color(red: 0.93, green: 0.96, blue: 0.90), Color(red: 0.99, green: 0.95, blue: 0.88)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .navigationTitle("决策结果")
    }

    private func winnerLabel(for venueId: UUID) -> String {
        viewModel.candidateVenue(for: venueId)?.name ?? venueId.uuidString
    }
}
