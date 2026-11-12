package dev.ward.domain.license;

/**
 * The policy's decision on one license expression.
 *
 * @param outcome whether the expression passes, or how it fails
 * @param entry the policy entry that decided it: the matching deny pattern, or the allow list;
 *     empty when the expression passes
 * @param license the identifier that failed, or empty when the expression passes
 */
public record LicenseVerdict(Outcome outcome, String entry, String license) {

  /** The decision. */
  public enum Outcome {
    /** The expression can be satisfied under the policy. */
    PASSED,
    /** A deny policy: every choice uses a denied license. */
    DENIED,
    /** An allow policy: every choice uses a license that is not allowed. */
    NOT_ALLOWED
  }

  /** The verdict of an expression the policy accepts. */
  public static LicenseVerdict passed() {
    return new LicenseVerdict(Outcome.PASSED, "", "");
  }

  /** Returns whether the expression passes. */
  public boolean ok() {
    return outcome == Outcome.PASSED;
  }
}
