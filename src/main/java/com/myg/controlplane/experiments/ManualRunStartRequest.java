package com.myg.controlplane.experiments;

import java.util.UUID;

public record ManualRunStartRequest(
        UUID approvalId
) {
}
