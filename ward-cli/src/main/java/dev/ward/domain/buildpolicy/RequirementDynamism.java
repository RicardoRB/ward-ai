package dev.ward.domain.buildpolicy;

import java.util.regex.Pattern;

/** How a Cargo, NuGet or Python version requirement can change. */
final class RequirementDynamism {
  private static final Pattern PYTHON_EXACT = Pattern.compile("(===?)\\s*[^*,\\s]+");

  private RequirementDynamism() {}

  /** {@code =1.2.3} is exact; a wildcard is always dynamic; any other requirement is a range. */
  static Dynamism cargo(final String text) {
    if (text.contains("*")) {
      return Dynamism.ALWAYS;
    }
    return text.startsWith("=") && !text.contains(",") ? Dynamism.NONE : Dynamism.RANGE;
  }

  /** A floating {@code 1.*} always moves; a range moves unless a lock file pins it. */
  static Dynamism dotnet(final String text) {
    if (text.contains("*")) {
      return Dynamism.ALWAYS;
    }
    final boolean exact = text.startsWith("[") && text.endsWith("]") && !text.contains(",");
    final boolean range = text.contains(",") || text.startsWith("(");
    return range && !exact ? Dynamism.RANGE : Dynamism.NONE;
  }

  static Dynamism python(final String text) {
    if (PYTHON_EXACT.matcher(text).matches() && !text.contains("*")) {
      return Dynamism.NONE;
    }
    return Dynamism.RANGE;
  }
}
