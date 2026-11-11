package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.domain.gate.LineHits;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads a report with the reader that accepts its first bytes, whatever the file is named. */
public final class DetectedLineCoverage implements LineCoverageReport {
  private static final int HEAD_BYTES = 256;

  private final List<LineCoverageReport> readers;

  /** Creates the reader that tries {@code readers} in order. */
  public DetectedLineCoverage(final List<LineCoverageReport> readers) {
    this.readers = List.copyOf(readers);
  }

  @Override
  public boolean accepts(final String head) {
    return readers.stream().anyMatch(reader -> reader.accepts(head));
  }

  @Override
  public LineHits read(final Path report) {
    final String head = head(report);
    return readers.stream()
        .filter(reader -> reader.accepts(head))
        .findFirst()
        .orElseThrow(
            () ->
                new LineCoverageException(
                    report.getFileName() + " is neither a JaCoCo XML report nor an lcov report"))
        .read(report);
  }

  private static String head(final Path report) {
    try (InputStream input = Files.newInputStream(report)) {
      return new String(input.readNBytes(HEAD_BYTES), StandardCharsets.UTF_8).replace("﻿", "");
    } catch (IOException e) {
      throw new LineCoverageException("Cannot read " + report.getFileName(), e);
    }
  }
}
