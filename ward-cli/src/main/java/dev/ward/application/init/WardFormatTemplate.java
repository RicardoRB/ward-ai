package dev.ward.application.init;

import dev.ward.domain.init.FormatGates;
import java.util.List;
import java.util.stream.Collectors;

/** Writes the {@code ward-format} gate, which Ward runs in-process, so it has no command. */
final class WardFormatTemplate {

  private WardFormatTemplate() {}

  /**
   * Appends the gate, enabled in {@code profiles}, which are already quoted and comma-separated.
   */
  static void append(final StringBuilder content, final List<String> profiles) {
    content
        .append("\n[checks.")
        .append(FormatGates.WARD_FORMAT)
        .append("]\nenabled = true\nprofiles = [")
        .append(
            profiles.stream()
                .map(profile -> "\"" + profile + "\"")
                .collect(Collectors.joining(", ")))
        .append("]\nuses = \"")
        .append(FormatGates.WARD_FORMAT)
        .append("\"\n");
  }
}
