package dev.ward.application.gate;

import dev.ward.domain.gate.CoverageFiles;
import dev.ward.domain.gate.LineCoverageReport;

/**
 * What the {@code crap} gate needs beside the analysis ports.
 *
 * @param reader reads a coverage report, whatever its format
 * @param files answers which report files exist and when files were written
 */
public record CrapPorts(LineCoverageReport reader, CoverageFiles files) {}
