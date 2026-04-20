package com.myg.controlplane.safety;

public record RunAssignmentSummary(
        long assignmentCount,
        long activeAssignmentCount,
        long stoppedAssignmentCount
) {
    public static RunAssignmentSummary empty() {
        return new RunAssignmentSummary(0, 0, 0);
    }
}
