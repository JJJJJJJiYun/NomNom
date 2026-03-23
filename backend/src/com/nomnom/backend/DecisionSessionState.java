package com.nomnom.backend;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class DecisionSessionState {
    private final UUID id;
    private final DecisionContext context;
    private final Deque<Restaurant> queue;
    private final List<DecisionRecordView> history;
    private Matchup activeMatchup;
    private Restaurant winner;

    public DecisionSessionState(UUID id, DecisionContext context, List<Restaurant> restaurants) {
        this.id = id;
        this.context = context;
        this.queue = new ArrayDeque<>(restaurants);
        this.history = new ArrayList<>();
        prepareNextMatchup();
    }

    public UUID id() { return id; }
    public DecisionContext context() { return context; }
    public Matchup activeMatchup() { return activeMatchup; }
    public Restaurant winner() { return winner; }
    public List<DecisionRecordView> history() { return List.copyOf(history); }
    public boolean completed() { return winner != null; }

    public void vote(DecisionWinner winnerChoice) {
        if (activeMatchup == null) {
            throw new IllegalStateException("No active matchup");
        }
        Restaurant selected = winnerChoice == DecisionWinner.LEFT ? activeMatchup.left() : activeMatchup.right();
        history.add(new DecisionRecordView(UUID.randomUUID(), activeMatchup.left().name(), activeMatchup.right().name(), selected.name()));
        queue.addLast(selected);
        prepareNextMatchup();
    }

    private void prepareNextMatchup() {
        activeMatchup = null;
        if (queue.size() == 1) {
            winner = queue.removeFirst();
            return;
        }
        if (queue.size() >= 2) {
            activeMatchup = new Matchup(UUID.randomUUID(), queue.removeFirst(), queue.removeFirst());
        }
    }

    public record Matchup(UUID matchupId, Restaurant left, Restaurant right) {}
    public enum DecisionWinner { LEFT, RIGHT }
}
