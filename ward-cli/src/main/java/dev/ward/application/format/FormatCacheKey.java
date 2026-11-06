package dev.ward.application.format;

import dev.ward.domain.format.FormatTarget;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Keys the {@code ward-format} gate's cached results by engine version, settings and content. */
final class FormatCacheKey {
  private static final String ALGORITHM = "SHA-256";

  private FormatCacheKey() {}

  /**
   * Returns the key of one file: the engine version, the language, the resolved settings and the
   * content with {@code \n} line endings.
   */
  static String of(final String wardVersion, final FormatTarget target, final String content) {
    final MessageDigest digest = digest();
    for (final String part :
        List.of(wardVersion, target.language().name(), target.settings().fingerprint(), content)) {
      digest.update((part.length() + ":" + part).getBytes(StandardCharsets.UTF_8));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance(ALGORITHM);
    } catch (final NoSuchAlgorithmException exception) {
      throw new IllegalStateException(ALGORITHM + " is not available", exception);
    }
  }
}
