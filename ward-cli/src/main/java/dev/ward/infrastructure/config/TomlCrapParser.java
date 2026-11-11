package dev.ward.infrastructure.config;

import dev.ward.domain.config.CrapOptions;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Parses the {@code report} and {@code max} of a {@code crap} check. */
final class TomlCrapParser {
  private static final String REPORT = "report";
  private static final String MAX = "max";
  private static final String PARENT = "..";

  private TomlCrapParser() {}

  /** Returns the options of a {@code crap} check, or {@code null} for a check of another type. */
  static CrapOptions parse(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.CRAP.equals(type)) {
      return null;
    }
    return new CrapOptions(reports(where, gate), max(where, gate));
  }

  private static int max(final String where, final TomlTable gate) {
    if (!gate.contains(MAX)) {
      return CrapOptions.DEFAULT_MAX;
    }
    final Long value = gate.isLong(MAX) ? gate.getLong(MAX) : null;
    if (value == null || value < 1 || value > Integer.MAX_VALUE) {
      throw new WardException("'" + where + "." + MAX + "' must be an integer of at least 1");
    }
    return value.intValue();
  }

  private static List<String> reports(final String where, final TomlTable gate) {
    if (!gate.contains(REPORT)) {
      return List.of();
    }
    final String key = where + "." + REPORT;
    if (gate.isString(REPORT)) {
      return List.of(path(key, gate.getString(REPORT)));
    }
    final TomlArray array = gate.isArray(REPORT) ? gate.getArray(REPORT) : null;
    if (array == null || array.isEmpty()) {
      throw new WardException("'" + key + "' must be a path or a non-empty array of paths");
    }
    final List<String> paths = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      paths.add(path(key, array.isString(index) ? array.getString(index) : null));
    }
    return paths;
  }

  /** Returns {@code value} normalized, or fails when it is not a path inside the project. */
  private static String path(final String key, final String value) {
    final String message = "'" + key + "' must name files inside the project, relative to its root";
    if (value == null || value.isBlank()) {
      throw new WardException(message);
    }
    try {
      final Path path = Path.of(value).normalize();
      if (path.isAbsolute() || path.startsWith(PARENT) || path.toString().isEmpty()) {
        throw new WardException(message);
      }
      return path.toString().replace('\\', '/');
    } catch (InvalidPathException e) {
      throw new WardException(message, e);
    }
  }
}
