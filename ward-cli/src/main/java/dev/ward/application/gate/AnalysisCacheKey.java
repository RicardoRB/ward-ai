package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Builds the cache keys of the {@code ward} gate's per-unit results. */
final class AnalysisCacheKey {
  private static final String ALGORITHM = "SHA-256";

  private AnalysisCacheKey() {}

  /**
   * Hashes the engine version and the rules selected for one language. The rules are hashed in id
   * order, so the order they were listed in does not change the hash.
   */
  static String ruleSet(final String engineVersion, final List<RuleDefinition> rules) {
    final List<String> parts = new ArrayList<>();
    parts.add(engineVersion);
    rules.stream()
        .sorted(Comparator.comparing(RuleDefinition::id))
        .forEach(
            rule -> {
              parts.add(rule.id());
              parts.add(rule.language().name());
              parts.add(rule.capability().name());
              parts.add(rule.severity().name());
              parts.add(rule.body());
            });
    return digest(parts);
  }

  /** Hashes the rule set and the unit's path and full text. */
  static String unit(final String ruleSetHash, final SourceUnit unit) {
    return digest(List.of(ruleSetHash, unit.path(), unit.text()));
  }

  /** Hashes each part behind its length, so no two different part lists share a digest. */
  private static String digest(final List<String> parts) {
    try {
      final MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      for (final String part : parts) {
        final byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
        digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (final NoSuchAlgorithmException exception) {
      throw new IllegalStateException(ALGORITHM + " is not available", exception);
    }
  }
}
