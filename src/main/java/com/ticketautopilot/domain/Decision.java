package com.ticketautopilot.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decisions")
public class Decision {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "category_confidence", nullable = false)
    private double categoryConfidence;

    @Column(nullable = false, length = 20)
    private String urgency;

    @Column(name = "urgency_confidence", nullable = false)
    private double urgencyConfidence;

    @Column(name = "auto_resolvable", nullable = false)
    private boolean autoResolvable;

    @Column(name = "auto_resolvable_confidence", nullable = false)
    private double autoResolvableConfidence;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "engine_used", nullable = false, length = 30)
    private String engineUsed;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected Decision() {
    }

    public Decision(UUID ticketId, String category, double categoryConfidence,
                     String urgency, double urgencyConfidence,
                     boolean autoResolvable, double autoResolvableConfidence,
                     String action, String engineUsed, int latencyMs) {
        this.ticketId = ticketId;
        this.category = category;
        this.categoryConfidence = categoryConfidence;
        this.urgency = urgency;
        this.urgencyConfidence = urgencyConfidence;
        this.autoResolvable = autoResolvable;
        this.autoResolvableConfidence = autoResolvableConfidence;
        this.action = action;
        this.engineUsed = engineUsed;
        this.latencyMs = latencyMs;
    }

    @PrePersist
    void onCreate() {
        if (decidedAt == null) {
            decidedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public String getCategory() {
        return category;
    }

    public double getCategoryConfidence() {
        return categoryConfidence;
    }

    public String getUrgency() {
        return urgency;
    }

    public double getUrgencyConfidence() {
        return urgencyConfidence;
    }

    public boolean isAutoResolvable() {
        return autoResolvable;
    }

    public double getAutoResolvableConfidence() {
        return autoResolvableConfidence;
    }

    public String getAction() {
        return action;
    }

    public String getEngineUsed() {
        return engineUsed;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
