package com.myg.controlplane.safety;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping({"/audit/events", "/safety/audit-records"})
    public List<SafetyAuditRecordResponse> listAuditRecords(@RequestParam Optional<String> action,
                                                            @RequestParam Optional<String> resourceType,
                                                            @RequestParam Optional<String> actor,
                                                            @RequestParam Optional<String> resourceId) {
        return auditLogService.findRecords(
                action.map(this::parseAction),
                resourceType.map(this::parseResourceType),
                actor.map(String::trim).filter(value -> !value.isEmpty()),
                resourceId.map(String::trim).filter(value -> !value.isEmpty())
        );
    }

    private SafetyAuditEventType parseAction(String rawAction) {
        try {
            return SafetyAuditEventType.valueOf(rawAction.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported audit action filter: " + rawAction);
        }
    }

    private AuditResourceType parseResourceType(String rawResourceType) {
        try {
            return AuditResourceType.valueOf(rawResourceType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported audit resource type filter: " + rawResourceType
            );
        }
    }
}
