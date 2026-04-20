package com.myg.controlplane.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final ChaosAuthProperties properties;
    private final CurrentSecurityActor currentSecurityActor;

    public AuthController(ChaosAuthProperties properties, CurrentSecurityActor currentSecurityActor) {
        this.properties = properties;
        this.currentSecurityActor = currentSecurityActor;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('chaos.view')")
    public AuthStatusResponse getCurrentUser(Authentication authentication) {
        return new AuthStatusResponse(
                currentSecurityActor.username(authentication),
                properties.getMode().name(),
                currentSecurityActor.roles(authentication),
                currentSecurityActor.permissions(authentication)
        );
    }
}
