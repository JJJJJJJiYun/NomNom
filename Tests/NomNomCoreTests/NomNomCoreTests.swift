import Testing
@testable import NomNomCore

@Test func recommendationSnapshotIncludesReasons() async throws {
    let restaurant = Restaurant.sampleData[0]
    let snapshot = RecommendationEngine.snapshot(for: restaurant, context: .dinnerForTwo)

    #expect(snapshot.reasons.count >= 3)
    #expect(snapshot.summary.contains("适合"))
    #expect(snapshot.pros.contains("牛舌口碑稳定"))
}

@Test func decisionSessionProducesWinner() async throws {
    var session = DecisionSession(restaurants: Array(Restaurant.sampleData.prefix(4)))

    while let matchup = session.currentMatchup {
        let outcome: DecisionOutcome = matchup.left.rating >= matchup.right.rating ? .left : .right
        session.choose(outcome)
    }

    #expect(session.isFinished)
    #expect(session.winner?.name == "山葵割烹")
    #expect(session.history.count == 3)
}
