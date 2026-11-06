package dev.ward.domain.init;

import dev.ward.domain.gate.SupportedQualityGates;
import java.util.Collection;
import java.util.Set;

/**
 * The format gates that the {@code ward} format engine replaces with {@code ward-format}, and the
 * linter that also formats: Biome stays as the lint gate with its formatter turned off.
 */
public final class FormatGates {
  /** The gate that runs the native formatter. */
  public static final String WARD_FORMAT = SupportedQualityGates.FORMAT;

  /**
   * The gates {@code ward-format} replaces: {@code format} (Spotless for the JVM), Prettier,
   * ruff-format and swift-format.
   */
  public static final Set<String> REPLACED =
      Set.of("format", "prettier", "ruff-format", "swift-format");

  /** Biome, which lints and formats in one gate. */
  public static final String BIOME = "biome";

  private FormatGates() {}

  /** Whether {@code ids} hold a gate that formats source files, so the format engine matters. */
  public static boolean formats(final Collection<String> ids) {
    return ids.stream().anyMatch(id -> REPLACED.contains(id) || BIOME.equals(id));
  }
}
