package com.myg.controlplane.safety;

import java.time.Instant;

public record KillSwitchState(
        boolean enabled,
        String lastEnabledBy,
        String lastEnableReason,
        Instant lastEnabledAt,
        String lastDisabledBy,
        String lastDisableReason,
        Instant lastDisabledAt,
        Instant updatedAt
) {
}
