package com.myg.controlplane.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class OidcRoleExtractor {

    private final ChaosAuthProperties properties;

    public OidcRoleExtractor(ChaosAuthProperties properties) {
        this.properties = properties;
    }

    public Set<PlatformRole> extract(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return EnumSet.noneOf(PlatformRole.class);
        }

        Object rawClaim = claims.get(properties.getOidc().getRoleClaim());
        if (rawClaim == null) {
            return EnumSet.noneOf(PlatformRole.class);
        }

        Stream<String> values;
        if (rawClaim instanceof Collection<?> collection) {
            values = collection.stream().map(String::valueOf);
        } else if (rawClaim instanceof String value) {
            values = Stream.of(value.split(","));
        } else {
            values = Stream.of(String.valueOf(rawClaim));
        }

        return values
                .map(PlatformRole::fromClaimValue)
                .flatMap(Optional::stream)
                .collect(() -> EnumSet.noneOf(PlatformRole.class), Set::add, Set::addAll);
    }
}
