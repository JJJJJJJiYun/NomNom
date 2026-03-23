import Foundation

public struct Matchup: Codable, Equatable, Sendable {
    public let left: Restaurant
    public let right: Restaurant
}

public enum DecisionOutcome: String, Codable, Sendable {
    case left
    case right
}

public struct DecisionRecord: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public let matchup: Matchup
    public let winner: Restaurant

    public init(id: UUID = UUID(), matchup: Matchup, winner: Restaurant) {
        self.id = id
        self.matchup = matchup
        self.winner = winner
    }
}

public struct DecisionSession: Sendable {
    private var queue: [Restaurant]
    private(set) public var currentMatchup: Matchup?
    private(set) public var history: [DecisionRecord] = []
    private(set) public var winner: Restaurant?

    public init(restaurants: [Restaurant]) {
        self.queue = restaurants
        self.currentMatchup = nil
        self.winner = nil
        prepareNextMatchup()
    }

    public var isFinished: Bool {
        winner != nil
    }

    public mutating func choose(_ outcome: DecisionOutcome) {
        guard let matchup = currentMatchup else { return }
        let selected = outcome == .left ? matchup.left : matchup.right
        history.append(DecisionRecord(matchup: matchup, winner: selected))
        queue.append(selected)
        prepareNextMatchup()
    }

    private mutating func prepareNextMatchup() {
        currentMatchup = nil

        if queue.count == 1 {
            winner = queue.removeFirst()
            return
        }

        guard queue.count >= 2 else { return }
        let left = queue.removeFirst()
        let right = queue.removeFirst()
        currentMatchup = Matchup(left: left, right: right)
    }
}
