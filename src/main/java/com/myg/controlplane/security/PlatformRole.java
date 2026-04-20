package com.myg.controlplane.security;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public enum PlatformRole {
    VIEWER(Set.of(ChaosAuthority.VIEW)),
    OPERATOR(Set.of(ChaosAuthority.VIEW, ChaosAuthority.OPERATE)),
    APPROVER(Set.of(ChaosAuthority.VIEW, ChaosAuthority.APPROVE)),
    ADMIN(Set.of(ChaosAuthority.VIEW, ChaosAuthority.OPERATE, ChaosAuthority.APPROVE, ChaosAuthority.ADMIN));

    private final Set<String> permissions;

    PlatformRole(Set<String> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<GrantedAuthority> toAuthorities() {
        LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + name()));
        permissions.stream()
                .sorted()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return authorities;
    }

    public static Set<PlatformRole> normalize(Collection<PlatformRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return EnumSet.noneOf(PlatformRole.class);
        }
        return roles.stream()
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PlatformRole.class)));
    }

    public static Set<GrantedAuthority> toAuthorities(Collection<PlatformRole> roles) {
        return normalize(roles).stream()
                .sorted(Comparator.comparing(Enum::name))
                .flatMap(role -> role.toAuthorities().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Optional<PlatformRole> fromClaimValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        if (normalized.startsWith("CHAOS_")) {
            normalized = normalized.substring(6);
        }

        try {
            return Optional.of(PlatformRole.valueOf(normalized));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
