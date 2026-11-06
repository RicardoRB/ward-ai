package dev.ward.analysis.domain.rule;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A named set of rules that {@code ward-rules.toml} can select. A rule row lists the presets it
 * belongs to in {@code presets}. {@link #STRICT} selects every rule of {@link #STANDARD} too, and
 * {@link #NONE} selects nothing, so a team builds its own set from {@code include}.
 */
public enum RulePreset {
  /** Selects no rule. */
  NONE,
  /** The rules of Checkstyle's {@code google_checks.xml} and Ward's own checks, and equivalents. */
  STANDARD,
  /** Every rule of {@link #STANDARD} and the stricter ones. */
  STRICT;

  /** Returns the preset named {@code key} in lower case, such as {@code "standard"}. */
  public static Optional<RulePreset> byKey(final String key) {
    for (final RulePreset preset : values()) {
      if (preset.key().equals(key)) {
        return Optional.of(preset);
      }
    }
    return Optional.empty();
  }

  /** Returns the name used in rule rows and in {@code ward-rules.toml}. */
  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Returns whether a rule that lists {@code listed} belongs to this preset. */
  public boolean selects(final Set<RulePreset> listed) {
    if (this == STRICT) {
      return listed.contains(STANDARD) || listed.contains(STRICT);
    }
    return this == STANDARD && listed.contains(STANDARD);
  }
}
