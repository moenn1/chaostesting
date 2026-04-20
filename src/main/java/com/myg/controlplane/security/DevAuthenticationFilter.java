package com.myg.controlplane.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class DevAuthenticationFilter extends OncePerRequestFilter {

    private final ChaosAuthProperties properties;

    public DevAuthenticationFilter(ChaosAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String username = request.getHeader(properties.getDev().getUserHeader());
            if (username == null || username.isBlank()) {
                username = properties.getDev().getDefaultUsername();
            }

            Set<PlatformRole> roles = readRoles(request.getHeader(properties.getDev().getRolesHeader()));
            if (roles.isEmpty()) {
                roles = PlatformRole.normalize(properties.getDev().getDefaultRoles());
            }

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(username.trim(), "N/A", PlatformRole.toAuthorities(roles));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private Set<PlatformRole> readRoles(String rawRoles) {
        if (rawRoles == null || rawRoles.isBlank()) {
            return EnumSet.noneOf(PlatformRole.class);
        }

        return Arrays.stream(rawRoles.split(","))
                .map(PlatformRole::fromClaimValue)
                .flatMap(Optional::stream)
                .collect(() -> EnumSet.noneOf(PlatformRole.class), Set::add, Set::addAll);
    }
}
