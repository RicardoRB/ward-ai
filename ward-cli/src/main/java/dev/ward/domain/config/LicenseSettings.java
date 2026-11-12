package dev.ward.domain.config;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The {@code [checks.license]} settings: a deny list or an allow list of SPDX identifiers (each may
 * end in {@code *} for a family), the exceptions that waive a denied license, the inventory scopes
 * that are checked, and the severity of an unknown license.
 *
 * @param mode whether {@code patterns} deny or allow
 * @param patterns the denied or allowed identifiers
 * @param exceptions the {@code WITH} exceptions that waive a deny; used only by a deny policy
 * @param scopes the lower-case inventory scope names that are checked; empty means every scope
 * @param unknownFails whether an unknown license fails the gate, or only warns
 */
public record LicenseSettings(
    Mode mode,
    List<String> patterns,
    List<String> exceptions,
    Set<String> scopes,
    boolean unknownFails) {
  /** The default deny list. */
  public static final List<String> DEFAULT_DENY = List.of("GPL-*", "AGPL-*");

  /** The default exceptions. */
  public static final List<String> DEFAULT_EXCEPTIONS = List.of("Classpath-exception-2.0");

  private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.:_+-]*\\*?");

  /** Whether the patterns deny or allow licenses. */
  public enum Mode {
    /** Licenses matching a pattern are denied. */
    DENY,
    /** Only licenses matching a pattern are allowed. */
    ALLOW
  }

  /** Copies the lists. */
  public LicenseSettings {
    patterns = List.copyOf(patterns);
    exceptions = List.copyOf(exceptions);
    scopes = Set.copyOf(scopes);
  }

  /** Returns the settings that apply when {@code [checks.license]} sets nothing. */
  public static LicenseSettings defaults() {
    return new LicenseSettings(Mode.DENY, DEFAULT_DENY, DEFAULT_EXCEPTIONS, Set.of(), true);
  }

  /** Returns whether {@code pattern} is a valid identifier or family such as {@code GPL-*}. */
  public static boolean validPattern(final String pattern) {
    return PATTERN.matcher(pattern).matches();
  }
}
