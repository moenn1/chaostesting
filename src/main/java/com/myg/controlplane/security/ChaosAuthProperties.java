package com.myg.controlplane.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chaos.auth")
public class ChaosAuthProperties {

    private ChaosAuthMode mode = ChaosAuthMode.OIDC;
    private final Dev dev = new Dev();
    private final Oidc oidc = new Oidc();

    public ChaosAuthMode getMode() {
        return mode;
    }

    public void setMode(ChaosAuthMode mode) {
        this.mode = mode == null ? ChaosAuthMode.OIDC : mode;
    }

    public Dev getDev() {
        return dev;
    }

    public Oidc getOidc() {
        return oidc;
    }

    public static final class Dev {
        private String defaultUsername = "local-admin";
        private List<PlatformRole> defaultRoles = new ArrayList<>(List.of(PlatformRole.ADMIN));
        private String userHeader = "X-Chaos-Dev-User";
        private String rolesHeader = "X-Chaos-Dev-Roles";

        public String getDefaultUsername() {
            return defaultUsername;
        }

        public void setDefaultUsername(String defaultUsername) {
            this.defaultUsername = defaultUsername;
        }

        public List<PlatformRole> getDefaultRoles() {
            return defaultRoles;
        }

        public void setDefaultRoles(List<PlatformRole> defaultRoles) {
            this.defaultRoles = defaultRoles == null ? new ArrayList<>() : new ArrayList<>(defaultRoles);
        }

        public String getUserHeader() {
            return userHeader;
        }

        public void setUserHeader(String userHeader) {
            this.userHeader = userHeader;
        }

        public String getRolesHeader() {
            return rolesHeader;
        }

        public void setRolesHeader(String rolesHeader) {
            this.rolesHeader = rolesHeader;
        }
    }

    public static final class Oidc {
        private String roleClaim = "groups";
        private String principalClaim = "preferred_username";

        public String getRoleClaim() {
            return roleClaim;
        }

        public void setRoleClaim(String roleClaim) {
            this.roleClaim = roleClaim;
        }

        public String getPrincipalClaim() {
            return principalClaim;
        }

        public void setPrincipalClaim(String principalClaim) {
            this.principalClaim = principalClaim;
        }
    }
}
