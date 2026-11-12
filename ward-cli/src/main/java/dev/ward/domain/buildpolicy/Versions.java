package dev.ward.domain.buildpolicy;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads versions the way each ecosystem writes them. */
public final class Versions {
  private static final String SNAPSHOT = "-SNAPSHOT";
  private static final Pattern NUMBERS = Pattern.compile("\\d+");
  private static final Pattern NODE_EXACT =
      Pattern.compile("v?\\d+(\\.\\d+){0,2}([-+][0-9A-Za-z.+-]+)?");
  private static final Pattern PYTHON_EXACT = Pattern.compile("(===?)\\s*[^*,\\s]+");
  private static final List<String> NODE_TAGS = List.of("latest", "next", "*", "x", "");
  private static final List<String> NON_REGISTRY =
      List.of(
          "git", "file:", "link:", "workspace:", "npm:", "http", "github:", "portal:", "patch:");

  private Versions() {}

  /** Returns how {@code version} of a {@code kind} in {@code ecosystem} can change. */
  public static Dynamism dynamism(
      final BuildEcosystem ecosystem, final DeclarationKind kind, final String version) {
    final String text = version.strip();
    return switch (ecosystem) {
      case MAVEN -> maven(text);
      case GRADLE -> gradle(kind, text);
      case NODE -> node(text);
      default -> python(text);
    };
  }

  /** Returns whether a Maven or Gradle plugin version is an exact version. */
  public static boolean exact(final BuildEcosystem ecosystem, final String version) {
    return !version.contains("${")
        && !version.isBlank()
        && dynamism(ecosystem, DeclarationKind.PLUGIN, version) == Dynamism.NONE;
  }

  private static Dynamism maven(final String text) {
    final boolean dynamic =
        text.contains("[")
            || text.contains("(")
            || text.contains(",")
            || "LATEST".equals(text)
            || "RELEASE".equals(text)
            || text.endsWith(SNAPSHOT);
    return dynamic ? Dynamism.ALWAYS : Dynamism.NONE;
  }

  private static Dynamism gradle(final DeclarationKind kind, final String text) {
    final boolean dynamic =
        text.contains("+")
            || text.startsWith("latest.")
            || text.contains("[")
            || text.contains("(")
            || text.endsWith(SNAPSHOT);
    if (!dynamic) {
      return Dynamism.NONE;
    }
    return kind == DeclarationKind.PLUGIN ? Dynamism.ALWAYS : Dynamism.RANGE;
  }

  private static Dynamism node(final String text) {
    final String lower = text.toLowerCase(Locale.ROOT);
    if (NODE_TAGS.contains(lower)) {
      return Dynamism.ALWAYS;
    }
    if (NON_REGISTRY.stream().anyMatch(lower::startsWith) || lower.contains("/")) {
      return Dynamism.NONE;
    }
    if (NODE_EXACT.matcher(text).matches() && !text.contains("x")) {
      return Dynamism.NONE;
    }
    if (lower.matches("[\\d.]*[x*]([.\\d x*]*)")) {
      return Dynamism.ALWAYS;
    }
    return unboundedAbove(lower) ? Dynamism.ALWAYS : Dynamism.RANGE;
  }

  /** A range such as {@code >=1.0.0} has no upper bound, so no lockfile makes it a decision. */
  private static boolean unboundedAbove(final String text) {
    return text.startsWith(">") && !text.contains("<");
  }

  private static Dynamism python(final String text) {
    if (PYTHON_EXACT.matcher(text).matches() && !text.contains("*")) {
      return Dynamism.NONE;
    }
    return Dynamism.RANGE;
  }

  /** Returns the numbers of {@code version}, such as {@code [3, 9, 9]} for {@code 3.9.9}. */
  public static List<Integer> numbers(final String version) {
    final Matcher matcher = NUMBERS.matcher(version);
    return matcher.results().map(result -> Integer.parseInt(result.group())).toList();
  }

  /** Compares dotted versions numerically; a missing part counts as zero. */
  public static int compare(final String left, final String right) {
    final List<Integer> a = numbers(left);
    final List<Integer> b = numbers(right);
    final int size = Math.max(a.size(), b.size());
    for (int index = 0; index < size; index++) {
      final int result =
          Integer.compare(index < a.size() ? a.get(index) : 0, index < b.size() ? b.get(index) : 0);
      if (result != 0) {
        return result;
      }
    }
    return 0;
  }
}
