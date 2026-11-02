package dev.ward.infrastructure.config;

import dev.ward.domain.config.WardException;
import dev.ward.domain.config.XcodePlatform;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.List;
import org.tomlj.TomlTable;

/**
 * Parses the {@code platform} of an {@code xcodebuild-test} check, which it needs unless its
 * command names a {@code -destination}.
 */
final class TomlPlatformParser {
  private static final String PLATFORM = "platform";
  private static final String DESTINATION = "-destination";

  private TomlPlatformParser() {}

  static XcodePlatform parse(
      final String where, final String type, final TomlTable gate, final List<String> command) {
    final String key = where + "." + PLATFORM;
    final boolean xcode = SupportedQualityGates.XCODEBUILD_TEST.equals(type);
    if (!gate.contains(PLATFORM)) {
      if (xcode && !command.contains(DESTINATION)) {
        throw new WardException(
            "'" + key + "' is required unless the command has " + DESTINATION + allowed());
      }
      return null;
    }
    if (!xcode) {
      throw new WardException("'" + key + "' is only supported for xcodebuild-test checks");
    }
    final String value = gate.isString(PLATFORM) ? gate.getString(PLATFORM) : null;
    return XcodePlatform.fromLabel(value)
        .orElseThrow(() -> new WardException("'" + key + "' is not supported" + allowed()));
  }

  private static String allowed() {
    return "; use one of " + XcodePlatform.labels();
  }
}
