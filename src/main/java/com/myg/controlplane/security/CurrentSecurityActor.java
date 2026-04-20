package com.myg.controlplane.security;

import java.util.Comparator;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class CurrentSecurityActor {

    private final ChaosAuthProperties properties;

    public CurrentSecurityActor(ChaosAuthProperties properties) {
        this.properties = properties;
    }

    public String username(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authenticated principal required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return firstNonBlank(
                    oidcUser.getClaimAsString(properties.getOidc().getPrincipalClaim()),
                    oidcUser.getPreferredUsername(),
                    oidcUser.getEmail(),
                    oidcUser.getSubject(),
                    authentication.getName()
            );
        }
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth2Principal) {
            Object claim = oauth2Principal.getAttributes().get(properties.getOidc().getPrincipalClaim());
            return firstNonBlank(claim == null ? null : String.valueOf(claim), authentication.getName());
        }
        return firstNonBlank(authentication.getName());
    }

    public List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> permissions(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("chaos."))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        throw new AccessDeniedException("Authenticated principal is missing a usable name");
    }
}
