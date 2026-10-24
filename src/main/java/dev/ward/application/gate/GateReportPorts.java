package dev.ward.application.gate;

import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.ReportDirectories;

/**
 * The ports gates use for the reports their tools write.
 *
 * @param directories creates a report's parent directory before a command runs
 * @param stryker reads Stryker's mutation report, or {@code null} when it is unavailable
 * @param pit reads PIT's mutation report, or {@code null} when it is unavailable
 */
public record GateReportPorts(
    ReportDirectories directories, MutationReport stryker, MutationReport pit) {}
