package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;

/** The Ward markers of generated tool configs, shared by every toolchain that writes one. */
public final class ConfigFiles {
  public static final String JS_MARKER = "// managed-by: ward";
  public static final String TOML_MARKER = "# managed-by: ward";

  private ConfigFiles() {}

  /** Returns whether {@code existing} starts with a Ward marker of a Node config file. */
  public static boolean owned(final String existing) {
    return existing.startsWith(JS_MARKER) || existing.startsWith(TOML_MARKER);
  }

  /** Returns {@code marker} with the preset that wrote the file, and a newline. */
  public static String marker(final String marker, final QualityPreset preset) {
    return marker + (preset == null ? "" : " preset=" + preset.id()) + "\n";
  }
}
