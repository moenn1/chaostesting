package com.myg.controlplane.security;

import java.util.List;

public record AuthStatusResponse(
        String username,
        String mode,
        List<String> roles,
        List<String> permissions
) {
}
