package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.FormatGates;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes the gates that Ward runs in-process and that take no command: {@code ward-format}, and
 * {@code license} with its policy. The strict preset allows only permissive licenses; every other
 * choice denies GPL and AGPL.
 */
final class InProcessGateTemplate {
  private static final List<String> STRICT_LICENSES =
      List.of(
          "Apache-2.0",
          "MIT",
          "BSD-2-Clause",
          "BSD-3-Clause",
          "ISC",
          "EPL-1.0",
          "EPL-2.0",
          "CC0-1.0");
  private static final List<String> DENIED_LICENSES = List.of("GPL-*", "AGPL-*");

  private InProcessGateTemplate() {}

  /** Returns whether {@code id} is written by {@link #append}. */
  static boolean handles(final String id) {
    return FormatGates.WARD_FORMAT.equals(id) || SupportedQualityGates.LICENSE.equals(id);
  }

  /**
   * Appends the gate {@code id}, enabled in {@code profiles}, which are already quoted and
   * comma-separated.
   */
  static void append(
      final StringBuilder content,
      final String id,
      final List<String> profiles,
      final QualityPreset preset) {
    if (FormatGates.WARD_FORMAT.equals(id)) {
      WardFormatTemplate.append(content, profiles);
      return;
    }
    content
        .append("\n[checks.")
        .append(id)
        .append("]\nenabled = true\nprofiles = [")
        .append(
            profiles.stream()
                .map(profile -> "\"" + profile + "\"")
                .collect(Collectors.joining(", ")))
        .append("]\nuses = \"")
        .append(id)
        .append("\"\n")
        .append(preset == QualityPreset.STRICT ? "allow = " : "deny = ")
        .append(
            (preset == QualityPreset.STRICT ? STRICT_LICENSES : DENIED_LICENSES)
                .stream()
                    .map(license -> "\"" + license + "\"")
                    .collect(Collectors.joining(", ", "[", "]\n")));
  }
}
