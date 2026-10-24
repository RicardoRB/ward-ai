package dev.ward.application.gate;

import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.ReportDirectories;

/**
 * The ports gates use for the reports their tools write.
 *
 * @param directories creates a report's parent directory before a command runs
 * @param mutations reads mutation reports, or {@code null} when the stryker gate cannot use one
 */
public record GateReportPorts(ReportDirectories directories, MutationReport mutations) {}
