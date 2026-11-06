package dev.ward.domain.init;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Which formatter checks the source files: the external formatters Ward generates ({@code tool},
 * the default) or the {@code ward} native formatter ({@code ward}), which the {@code ward-format}
 * gate runs and which reads its layout from {@code .editorconfig}.
 */
public enum FormatEngine {
  /** The external formatters, such as Spotless, Prettier, ruff-format and swift-format. */
  TOOL,
  /** The native formatter, run by the {@code ward-format} gate. */
  WARD;

  /** The supported values, in the order they are listed to the user. */
  public static final String SUPPORTED = "tool, ward";

  /** Returns the engine named {@code id}, case-insensitively, or empty when none has that name. */
  public static Optional<FormatEngine> parse(final String id) {
    return Arrays.stream(values())
        .filter(engine -> engine.name().equalsIgnoreCase(id.strip()))
        .findFirst();
  }

  /** Returns the value written on the command line. */
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }
}
