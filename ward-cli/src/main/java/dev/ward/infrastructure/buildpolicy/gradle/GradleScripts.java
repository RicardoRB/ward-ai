package dev.ward.infrastructure.buildpolicy.gradle;

import dev.ward.domain.buildpolicy.BuildEcosystem;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.domain.buildpolicy.DeclarationKind;
import dev.ward.domain.buildpolicy.gradle.GradleCorePlugins;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A lexical reader of Gradle scripts, not a parser. It recognizes a closed list of forms in Groovy
 * and Kotlin DSL: {@code id("x") version "1"}, {@code kotlin("jvm") version "1"}, dependency
 * configurations with a {@code "group:artifact:version"} string, and {@code classpath} entries.
 * Text outside those forms is ignored; a recognized form whose version is not a literal is reported
 * as undeterminable.
 */
final class GradleScripts {
  private static final String QUOTED = "[\"']([^\"']*)[\"']";
  private static final Pattern PLUGIN =
      Pattern.compile(
          "\\b(id|kotlin)\\s*\\(?\\s*"
              + QUOTED
              + "\\s*\\)?\\s+version\\s+(\\(?\\s*"
              + QUOTED
              + "|[^\\s)]+)");
  private static final Pattern DEPENDENCY =
      Pattern.compile("^\\s*(\\w+)\\s*\\(?\\s*" + QUOTED + "\\s*\\)?\\s*$");
  private static final List<String> CONFIGURATIONS =
      List.of(
          "implementation",
          "api",
          "compileOnly",
          "runtimeOnly",
          "testImplementation",
          "testCompileOnly",
          "testRuntimeOnly",
          "annotationProcessor",
          "kapt",
          "ksp",
          "developmentOnly",
          "classpath");
  private static final int COORDINATE_PARTS = 3;

  private GradleScripts() {}

  /** Reads the declarations of the script {@code text} of {@code file}. */
  static List<Declaration> read(final String text, final String file, final boolean locked) {
    final List<Declaration> out = new ArrayList<>();
    final String[] lines = text.split("\n", -1);
    for (int index = 0; index < lines.length; index++) {
      final int line = index + 1;
      plugin(lines[index], file, line, out);
      dependency(lines[index], file, line, locked, out);
    }
    return out;
  }

  private static void plugin(
      final String text, final String file, final int line, final List<Declaration> out) {
    final Matcher matcher = PLUGIN.matcher(text);
    if (!matcher.find()) {
      return;
    }
    final String id =
        "kotlin".equals(matcher.group(1))
            ? "org.jetbrains.kotlin." + matcher.group(2)
            : matcher.group(2);
    if (GradleCorePlugins.contains(id)) {
      return;
    }
    final String version = matcher.group(4);
    out.add(
        new Declaration(
            BuildEcosystem.GRADLE,
            DeclarationKind.PLUGIN,
            id,
            version,
            "plugins",
            "",
            file,
            line,
            version == null ? Declaration.Flag.UNDETERMINABLE : Declaration.Flag.NONE));
  }

  private static void dependency(
      final String text,
      final String file,
      final int line,
      final boolean locked,
      final List<Declaration> out) {
    final Matcher matcher = DEPENDENCY.matcher(text);
    if (!matcher.find() || !CONFIGURATIONS.contains(matcher.group(1))) {
      return;
    }
    final String[] parts = matcher.group(2).split(":", -1);
    if (parts.length < COORDINATE_PARTS) {
      return;
    }
    final boolean classpath = "classpath".equals(matcher.group(1));
    final boolean literal = !parts[2].contains("$");
    out.add(
        new Declaration(
            BuildEcosystem.GRADLE,
            classpath ? DeclarationKind.PLUGIN : DeclarationKind.DEPENDENCY,
            parts[0] + ":" + parts[1],
            literal ? parts[2] : null,
            matcher.group(1),
            "",
            file,
            line,
            flag(literal, locked && !classpath)));
  }

  private static Declaration.Flag flag(final boolean literal, final boolean locked) {
    if (!literal) {
      return Declaration.Flag.UNDETERMINABLE;
    }
    return locked ? Declaration.Flag.LOCKED : Declaration.Flag.NONE;
  }

  /** Returns the module directories that {@code settings} includes, with {@code /}. */
  static List<String> includes(final String settings) {
    final List<String> modules = new ArrayList<>();
    final Matcher include = Pattern.compile("(?m)\\binclude\\s*\\(?([^)\\n]*)").matcher(settings);
    while (include.find()) {
      final Matcher name = Pattern.compile(QUOTED).matcher(include.group(1));
      while (name.find()) {
        modules.add(name.group(1).replaceFirst("^:", "").replace(':', '/'));
      }
    }
    return modules;
  }
}
