package com.myg.controlplane.security;

import java.util.LinkedHashSet;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ChaosAuthProperties properties,
                                            GrantedAuthoritiesMapper oidcAuthoritiesMapper) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/error", "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/agents/register", "/agents/heartbeat").permitAll()
                .requestMatchers(PathRequest.toH2Console()).hasAuthority(ChaosAuthority.ADMIN)
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.js", "/styles.css").hasAuthority(ChaosAuthority.VIEW)
                .requestMatchers(HttpMethod.GET, "/experiments/**", "/live-runs/**", "/results/**", "/history/**", "/fleet/**", "/agents/**")
                .hasAuthority(ChaosAuthority.VIEW)
                .requestMatchers(HttpMethod.GET, "/api/experiments", "/api/experiments/*").hasAuthority(ChaosAuthority.VIEW)
                .requestMatchers(HttpMethod.GET, "/audit/events", "/safety/audit-records", "/safety/runs", "/safety/kill-switch", "/auth/me")
                .hasAuthority(ChaosAuthority.VIEW)
                .requestMatchers(HttpMethod.POST, "/api/experiments").hasAuthority(ChaosAuthority.OPERATE)
                .requestMatchers(HttpMethod.POST, "/api/experiments/*/runs").hasAuthority(ChaosAuthority.OPERATE)
                .requestMatchers(HttpMethod.PUT, "/api/experiments/*").hasAuthority(ChaosAuthority.OPERATE)
                .requestMatchers(HttpMethod.DELETE, "/api/experiments/*").hasAuthority(ChaosAuthority.OPERATE)
                .requestMatchers(HttpMethod.POST, "/safety/dispatches/validate", "/safety/dispatches", "/safety/runs/*/stop")
                .hasAuthority(ChaosAuthority.OPERATE)
                .requestMatchers(HttpMethod.POST, "/safety/approvals").hasAuthority(ChaosAuthority.APPROVE)
                .requestMatchers(HttpMethod.POST, "/safety/kill-switch/enable", "/safety/kill-switch/disable")
                .hasAuthority(ChaosAuthority.ADMIN)
                .anyRequest().authenticated()
        );

        if (properties.getMode() == ChaosAuthMode.DEV) {
            http.addFilterBefore(new DevAuthenticationFilter(properties), AnonymousAuthenticationFilter.class);
            http.formLogin(formLogin -> formLogin.disable());
            http.oauth2Login(oauth2 -> oauth2.disable());
        } else {
            http.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(oidcAuthoritiesMapper))
            );
            http.logout(Customizer.withDefaults());
        }

        return http.build();
    }

    @Bean
    GrantedAuthoritiesMapper oidcAuthoritiesMapper(OidcRoleExtractor oidcRoleExtractor) {
        return authorities -> {
            LinkedHashSet<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
            authorities.stream()
                    .filter(authority -> authority instanceof OidcUserAuthority)
                    .map(authority -> (OidcUserAuthority) authority)
                    .map(OidcUserAuthority::getAttributes)
                    .map(oidcRoleExtractor::extract)
                    .flatMap(roleSet -> roleSet.stream().flatMap(role -> role.toAuthorities().stream()))
                    .forEach(mapped::add);
            return mapped;
        };
    }
}
