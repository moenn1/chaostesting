package com.myg.controlplane.safety;

public record GuardrailViolation(GuardrailViolationCode code, String message) {
}
