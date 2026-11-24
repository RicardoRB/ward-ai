package dev.ward.analysis.domain.swift;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The async requirements of Apple SDK protocols, read from the data file {@code
 * dev/ward/analysis/lang/swift/sdk/async-requirements.txt}: protocol simple name to the keys ({@code
 * name(label:label:)}) of its async methods.
 */
final class SwiftSdkAsyncRequirements {
  private static final String RESOURCE = "/dev/ward/analysis/lang/swift/sdk/async-requirements.txt";
  private static final String COMMENT = "#";
  private static final String SEPARATOR = "\\|";
  private static final int FIELDS = 2;

  private SwiftSdkAsyncRequirements() {}

  /** A fresh, mutable copy of the table, so project protocols can be added to it. */
  static Map<String, Set<String>> load() {
    final Map<String, Set<String>> table = new HashMap<>();
    try (InputStream stream = SwiftSdkAsyncRequirements.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("missing resource " + RESOURCE);
      }
      final BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      reader.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith(COMMENT))
          .map(line -> line.split(SEPARATOR))
          .filter(fields -> fields.length == FIELDS)
          .forEach(
              fields ->
                  table.computeIfAbsent(fields[0].strip(), key -> new HashSet<>()).add(
                      fields[1].strip()
                  )
          );
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return table;
  }
}
