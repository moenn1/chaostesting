package com.myg.controlplane.safety;

import java.time.Instant;

public record KillSwitchStatusResponse(
        boolean enabled,
        String lastEnabledBy,
        String lastEnableReason,
        Instant lastEnabledAt,
        String lastDisabledBy,
        String lastDisableReason,
        Instant lastDisabledAt,
        Instant updatedAt,
        long activeRunCount,
        long stopRequestedRunCount,
        long rolledBackRunCount,
        long stopRequestsIssued
) {
    public static KillSwitchStatusResponse from(KillSwitchState state,
                                                long activeRunCount,
                                                long stopRequestedRunCount,
                                                long rolledBackRunCount,
                                                long stopRequestsIssued) {
        return new KillSwitchStatusResponse(
                state.enabled(),
                state.lastEnabledBy(),
                state.lastEnableReason(),
                state.lastEnabledAt(),
                state.lastDisabledBy(),
                state.lastDisableReason(),
                state.lastDisabledAt(),
                state.updatedAt(),
                activeRunCount,
                stopRequestedRunCount,
                rolledBackRunCount,
                stopRequestsIssued
        );
    }
}
