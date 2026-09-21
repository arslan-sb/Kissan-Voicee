package com.kissanvoice.outbox;

/**
 * The aggregates that write outbox events, and the topic each one publishes
 * to (see docs/ROADMAP.md §7). Keeping the mapping on the enum means a new
 * aggregate type can't be added without deciding where its events go.
 */
public enum AggregateType {

    CONTRIBUTOR("kissan.contributor.v1"),
    RECORDING("kissan.recording.v1"),
    MILESTONE("kissan.milestone.v1");

    private final String topic;

    AggregateType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}
