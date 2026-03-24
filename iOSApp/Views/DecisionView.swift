import SwiftUI

struct DecisionView: View {
    @ObservedObject var viewModel: AppViewModel
    let onFinished: () -> Void

    var body: some View {
        Group {
            if let matchup = viewModel.currentMatchup {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        Text("留下今晚最想去的一家")
                            .font(.system(size: 28, weight: .bold, design: .rounded))

                        MatchupCard(title: "左边", venue: matchup.left, isVoting: viewModel.isVoting) {
                            Task {
                                await viewModel.choose(matchup.left.venueId)
                                if viewModel.result != nil { onFinished() }
                            }
                        }

                        MatchupCard(title: "右边", venue: matchup.right, isVoting: viewModel.isVoting) {
                            Task {
                                await viewModel.choose(matchup.right.venueId)
                                if viewModel.result != nil { onFinished() }
                            }
                        }

                        if !viewModel.history.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("已完成的对战")
                                    .font(.headline)
                                ForEach(viewModel.history) { record in
                                    Text("第 \(record.round) 轮 · 胜者 \(shortName(for: record.winnerVenueId))")
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .padding(16)
                            .background(Color.white.opacity(0.78), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        }
                    }
                    .padding(20)
                }
                .background(
                    LinearGradient(
                        colors: [Color(red: 0.97, green: 0.94, blue: 0.88), Color(red: 0.92, green: 0.95, blue: 0.98)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            } else if viewModel.isVoting {
                ProgressView("正在提交你的选择…")
            } else {
                ProgressView("等待后端返回对战数据…")
            }
        }
        .navigationTitle("两两 PK")
    }

    private func shortName(for venueId: UUID) -> String {
        viewModel.candidateVenue(for: venueId)?.name ?? (String(venueId.uuidString.prefix(6)) + "…")
    }
}

private struct MatchupCard: View {
    let title: String
    let venue: DecisionVenueSummaryDTO
    let isVoting: Bool
    let chooseAction: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                statusBadge
            }

            Text(venue.name)
                .font(.title3.bold())
            HStack(spacing: 14) {
                Label("¥\(venue.avgPrice)", systemImage: "yensign.circle")
                Label(String(format: "%.1f", venue.rating), systemImage: "star.fill")
                    .foregroundStyle(.orange)
            }
            .font(.subheadline)

            Button(isVoting ? "提交中…" : "选这家") {
                chooseAction()
            }
            .buttonStyle(.borderedProminent)
            .disabled(isVoting)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.82), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private var statusBadge: some View {
        Text(venue.openStatus == "OPEN" ? "营业中" : venue.openStatus == "CLOSED" ? "已打烊" : "状态未知")
            .font(.caption)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background((venue.openStatus == "OPEN" ? Color.green : Color.orange).opacity(0.12), in: Capsule())
            .foregroundStyle(venue.openStatus == "OPEN" ? Color.green : Color.orange)
    }
}
