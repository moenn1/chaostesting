package com.myg.controlplane.safety;

final class RunStatusMessages {

    private RunStatusMessages() {
    }

    static String activationMessage(ChaosRun run) {
        return faultSummary(run) + " activated.";
    }

    static String activeTelemetryMessage(ChaosRun run) {
        return faultSummary(run) + " remains active.";
    }

    static String rollbackMessage(ChaosRun run, String reason) {
        return switch (run.faultType()) {
            case "process_kill" -> "Process recovery verified after " + reason + ".";
            case "service_pause" -> "Timed service pause cleanup verified after " + reason + ".";
            case "request_drop" -> "Request-drop cleanup verified after " + reason + ".";
            case "http_error" -> "HTTP error rollback verified after " + reason + ".";
            default -> "Latency rollback verified after " + reason + ".";
        };
    }

    static String currentStatusMessage(ChaosRun run) {
        return switch (run.status()) {
            case ACTIVE -> activationMessage(run);
            case STOP_REQUESTED -> "Stop requested for " + faultSummary(run).toLowerCase() + ".";
            case ROLLED_BACK -> rollbackMessage(run, rollbackReason(run));
            case FAILED -> "Execution failure reported; inspect run reports for details.";
            case STOPPED -> "Run stopped before rollback verification completed.";
            case COMPLETED -> "Run completed.";
        };
    }

    private static String rollbackReason(ChaosRun run) {
        if (run.stopCommandReason() != null && !run.stopCommandReason().isBlank()) {
            return run.stopCommandReason();
        }
        return "rollback verification";
    }

    private static String faultSummary(ChaosRun run) {
        return switch (run.faultType()) {
            case "request_drop" -> "Request-drop injection at %s%%".formatted(run.dropPercentage());
            case "latency" -> latencySummary(run);
            case "http_error" -> "HTTP %s injection across %s"
                    .formatted(
                            run.errorCode() == null ? "error" : run.errorCode(),
                            trafficSummary(run)
                    );
            case "process_kill" -> "Process kill against %s".formatted(run.targetSelector());
            case "service_pause" -> "Timed service pause for %s".formatted(run.targetSelector());
            default -> "Fault '" + run.faultType() + "'";
        };
    }

    private static String latencySummary(ChaosRun run) {
        if (run.latencyMinimumMilliseconds() != null && run.latencyMaximumMilliseconds() != null) {
            return "Random latency between %sms and %sms across %s"
                    .formatted(run.latencyMinimumMilliseconds(), run.latencyMaximumMilliseconds(), trafficSummary(run));
        }
        if (run.latencyJitterMilliseconds() != null && run.latencyMilliseconds() != null) {
            return "Latency %sms +/- %sms across %s"
                    .formatted(run.latencyMilliseconds(), run.latencyJitterMilliseconds(), trafficSummary(run));
        }
        if (run.latencyMilliseconds() != null) {
            return "Latency %sms across %s".formatted(run.latencyMilliseconds(), trafficSummary(run));
        }
        return "Latency fault across %s".formatted(trafficSummary(run));
    }

    private static String trafficSummary(ChaosRun run) {
        return run.trafficPercentage() == null ? "traffic" : run.trafficPercentage() + "% of traffic";
    }
}
