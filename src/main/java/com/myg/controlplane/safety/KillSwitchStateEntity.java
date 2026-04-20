package com.myg.controlplane.safety;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kill_switch_state")
public class KillSwitchStateEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    private String lastEnabledBy;

    @Column(length = 512)
    private String lastEnableReason;

    private Instant lastEnabledAt;

    private String lastDisabledBy;

    @Column(length = 512)
    private String lastDisableReason;

    private Instant lastDisabledAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected KillSwitchStateEntity() {
    }

    public KillSwitchStateEntity(Long id, boolean enabled, Instant updatedAt) {
        this.id = id;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public void enable(String operator, String reason, Instant now) {
        enabled = true;
        lastEnabledBy = operator;
        lastEnableReason = reason;
        lastEnabledAt = now;
        updatedAt = now;
    }

    public void disable(String operator, String reason, Instant now) {
        enabled = false;
        lastDisabledBy = operator;
        lastDisableReason = reason;
        lastDisabledAt = now;
        updatedAt = now;
    }

    public KillSwitchState toDomain() {
        return new KillSwitchState(
                enabled,
                lastEnabledBy,
                lastEnableReason,
                lastEnabledAt,
                lastDisabledBy,
                lastDisableReason,
                lastDisabledAt,
                updatedAt
        );
    }
}
