package dev.ward.infrastructure.analysis.jvm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the module directories a Maven POM or a Gradle settings script lists, from text, so that
 * the modules of any Git revision can be found without running the build. A POM wins over a
 * settings script.
 *
 * <p>shortcut: Gradle {@code include} is read when its arguments are string literals on one line;
 * {@code projectDir} remapping and computed lists are not seen, so read them if such a build shows
 * up.
 */
public final class JvmModuleLayouts {
  private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
  private static final Pattern MAVEN_MODULE = Pattern.compile("<module>\\s*([^<]+?)\\s*</module>");
  private static final String POM_SUFFIX = "/pom.xml";
  private static final Pattern GRADLE_INCLUDE = Pattern.compile("(?m)^\\s*include\\b(.*)$");
  private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]+)['\"]");

  private JvmModuleLayouts() {}

  /**
   * Returns the listed module directories, relative to the build file, without duplicates.
   *
   * @param pomText the text of {@code pom.xml}, if there is one
   * @param gradleSettingsText the text of the Gradle settings script, if there is one
   */
  public static List<String> listed(
      final Optional<String> pomText, final Optional<String> gradleSettingsText) {
    if (pomText.isPresent()) {
      return maven(pomText.get());
    }
    return gradleSettingsText.map(JvmModuleLayouts::gradle).orElse(List.of());
  }

  private static List<String> maven(final String pom) {
    final Matcher module = MAVEN_MODULE.matcher(COMMENT.matcher(pom).replaceAll(""));
    final List<String> modules = new ArrayList<>();
    while (module.find()) {
      final String listed = module.group(1);
      add(
          modules,
          listed.endsWith(POM_SUFFIX)
              ? listed.substring(0, listed.length() - POM_SUFFIX.length())
              : listed);
    }
    return List.copyOf(modules);
  }

  private static List<String> gradle(final String settings) {
    final List<String> modules = new ArrayList<>();
    final Matcher include = GRADLE_INCLUDE.matcher(settings);
    while (include.find()) {
      final Matcher project = QUOTED.matcher(include.group(1));
      while (project.find()) {
        add(modules, project.group(1).replaceAll("^:+", "").replace(':', '/'));
      }
    }
    return List.copyOf(modules);
  }

  private static void add(final List<String> modules, final String directory) {
    final String normalized = directory.replaceAll("^\\./", "").replaceAll("/+$", "");
    if (!normalized.isEmpty() && !modules.contains(normalized)) {
      modules.add(normalized);
    }
  }
}
