package dev.ward.infrastructure.analysis.jvm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the literal Kotlin source directories of a build file: the kotlin-maven-plugin {@code
 * <sourceDirs>} and the Gradle {@code kotlin { srcDir }} blocks of the main and test source sets. A
 * directory under {@code src/test} is a test directory.
 */
final class KotlinSourceDirs {
  static final String MAIN_DEFAULT = "src/main/kotlin";
  static final String TEST_DEFAULT = "src/test/kotlin";
  private static final String TEST_SEGMENT = "src/test";
  private static final String TEST_SET = "test";
  private static final String MAVEN_DIRS = "<sourceDirs>(.*?)</sourceDirs>";
  private static final Pattern MAVEN_DIR =
      Pattern.compile("<sourceDir>\\s*([^<$]+?)\\s*</sourceDir>");
  private static final Pattern GRADLE_KOTLIN =
      Pattern.compile(
          "(main|test)\\s*\\{\\s*(?:java\\s*\\{[^}]*\\}\\s*)?kotlin\\s*\\{\\s*srcDirs?\\s*[(=]?\\s*\\[?\\s*['\"]([^'\"$]+)['\"]");

  private KotlinSourceDirs() {}

  /** A literal Kotlin source directory of a module, and whether it holds test sources. */
  record KotlinDir(String path, boolean test) {}

  /**
   * Reads the {@code <sourceDir>} values of the kotlin-maven-plugin source lists of a POM build.
   */
  static List<KotlinDir> maven(final String build) {
    final List<KotlinDir> dirs = new ArrayList<>();
    final Matcher lists = Pattern.compile(MAVEN_DIRS, Pattern.DOTALL).matcher(build);
    while (lists.find()) {
      final Matcher dir = MAVEN_DIR.matcher(lists.group(1));
      while (dir.find()) {
        dirs.add(new KotlinDir(dir.group(1), dir.group(1).startsWith(TEST_SEGMENT)));
      }
    }
    return dirs;
  }

  /** Reads the {@code srcDir} values of the Kotlin blocks of a Gradle script. */
  static List<KotlinDir> gradle(final String text) {
    final List<KotlinDir> dirs = new ArrayList<>();
    final Matcher match = GRADLE_KOTLIN.matcher(text);
    while (match.find()) {
      dirs.add(new KotlinDir(match.group(2), TEST_SET.equals(match.group(1))));
    }
    return dirs;
  }
}
