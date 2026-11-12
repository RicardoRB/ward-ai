package dev.ward.infrastructure.dependency.swift;

import dev.ward.domain.dependency.LockfileFormatException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the packages a {@code Package.swift} or an Xcode {@code project.pbxproj} depends on by
 * their literal repository locations. A {@code .package} call whose argument is not a string
 * literal cannot be matched to a pin and is an error, never a silent transitive package.
 */
final class SwiftManifest {
  private static final Pattern PACKAGE_CALL = Pattern.compile("\\.package\\s*\\(");
  private static final Pattern URL_ARGUMENT =
      Pattern.compile("\\.package\\s*\\(\\s*(?:name:\\s*\"[^\"]*\"\\s*,\\s*)?url:\\s*\"([^\"]+)\"");
  private static final Pattern PATH_ARGUMENT =
      Pattern.compile("\\.package\\s*\\(\\s*(?:name:\\s*\"[^\"]*\"\\s*,\\s*)?path:\\s*\"[^\"]+\"");
  private static final Pattern REPOSITORY_URL =
      Pattern.compile("repositoryURL\\s*=\\s*\"([^\"]+)\"");

  private SwiftManifest() {}

  /**
   * Returns the normalized locations of the {@code .package(url:)} literals of {@code source}.
   *
   * @throws LockfileFormatException if a {@code .package} argument is not a literal
   */
  static Set<String> packageSwift(final String source) {
    final String code = withoutComments(source);
    final Set<String> locations = new LinkedHashSet<>();
    final Matcher urls = URL_ARGUMENT.matcher(code);
    while (urls.find()) {
      locations.add(SwiftLocations.normalize(urls.group(1)));
    }
    final int literals =
        locations.size() + count(PATH_ARGUMENT, code) + duplicates(code, locations);
    if (count(PACKAGE_CALL, code) != literals) {
      throw new LockfileFormatException(
          "a .package dependency has an argument that is not a string literal, so it cannot be"
              + " matched to a pin");
    }
    return locations;
  }

  /** Drops {@code //} and block comments, leaving string literals such as URLs untouched. */
  private static String withoutComments(final String source) {
    final StringBuilder code = new StringBuilder(source.length());
    int index = 0;
    while (index < source.length()) {
      final char current = source.charAt(index);
      if (current == '"') {
        final int end = endOfString(source, index);
        code.append(source, index, end);
        index = end;
      } else if (source.startsWith("//", index)) {
        final int end = source.indexOf('\n', index);
        index = end < 0 ? source.length() : end;
      } else if (source.startsWith("/*", index)) {
        final int end = source.indexOf("*/", index + 2);
        index = end < 0 ? source.length() : end + 2;
      } else {
        code.append(current);
        index++;
      }
    }
    return code.toString();
  }

  private static int endOfString(final String source, final int start) {
    int index = start + 1;
    while (index < source.length() && source.charAt(index) != '"') {
      index += source.charAt(index) == '\\' ? 2 : 1;
    }
    return Math.min(index + 1, source.length());
  }

  /** Returns the normalized {@code repositoryURL} locations of an Xcode project file. */
  static Set<String> pbxproj(final String source) {
    final Set<String> locations = new LinkedHashSet<>();
    final Matcher urls = REPOSITORY_URL.matcher(source);
    while (urls.find()) {
      locations.add(SwiftLocations.normalize(urls.group(1)));
    }
    return locations;
  }

  private static int count(final Pattern pattern, final String text) {
    return (int) pattern.matcher(text).results().count();
  }

  /** Counts url literals that repeat a location, which the set collapsed. */
  private static int duplicates(final String code, final Set<String> locations) {
    return count(URL_ARGUMENT, code) - locations.size();
  }
}
