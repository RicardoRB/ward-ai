package dev.ward.infrastructure.init;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Edits a copied matrix fixture before {@code ward init}: the package versions a row declares, and
 * a lint violation in the generated Astro folder.
 */
final class NodeMatrixFixtureEdits {
  private static final String PACKAGE_JSON = "package.json";
  // Matches a whole dependency object, so the scripts section and its entries are never touched.
  private static final Pattern SECTION =
      Pattern.compile("\"(?:dependencies|devDependencies)\"\\s*:\\s*\\{[^}]*\\}");
  private static final Path ASTRO_IGNORED_FILE = Path.of(".astro", "ward-matrix-ignored.ts");
  // Breaks no-var and no-explicit-any in the generated ESLint config, and is Prettier-formatted.
  private static final String ASTRO_IGNORED_SOURCE = "export var ignored: any = 1;\n";

  private NodeMatrixFixtureEdits() {}

  /**
   * Parses a row's {@code packages} column: space-separated {@code name@version} entries, where the
   * name may be scoped ({@code @astrojs/check@0.9.12}).
   */
  static Map<String, String> parsePackages(final String spec) {
    final Map<String, String> packages = new LinkedHashMap<>();
    if (spec.isBlank()) {
      return packages;
    }
    for (final String entry : spec.trim().split("\\s+")) {
      final int at = entry.lastIndexOf('@');
      packages.put(entry.substring(0, at), entry.substring(at + 1));
    }
    return packages;
  }

  /**
   * Sets each package in the copied fixture's {@code package.json}, as {@link #withPackages} does.
   */
  static void declarePackages(final Path project, final Map<String, String> packages)
      throws IOException {
    if (packages.isEmpty()) {
      return;
    }
    final Path file = project.resolve(PACKAGE_JSON);
    Files.writeString(file, withPackages(Files.readString(file), packages));
  }

  /**
   * Returns {@code content} with each package set to its exact version in the {@code dependencies}
   * or {@code devDependencies} object that declares it, and added to {@code devDependencies} when
   * neither does.
   */
  static String withPackages(final String content, final Map<String, String> packages) {
    final Map<String, String> missing = new LinkedHashMap<>(packages);
    final Matcher section = SECTION.matcher(content);
    final StringBuilder out = new StringBuilder();
    while (section.find()) {
      String text = section.group();
      for (final Map.Entry<String, String> entry : packages.entrySet()) {
        final Pattern declared =
            Pattern.compile("\"" + Pattern.quote(entry.getKey()) + "\"\\s*:\\s*\"[^\"]*\"");
        if (declared.matcher(text).find()) {
          text =
              declared
                  .matcher(text)
                  .replaceAll(
                      Matcher.quoteReplacement(
                          "\"" + entry.getKey() + "\": \"" + entry.getValue() + "\""));
          missing.remove(entry.getKey());
        }
      }
      section.appendReplacement(out, Matcher.quoteReplacement(text));
    }
    section.appendTail(out);
    return missing.isEmpty()
        ? out.toString()
        : PackageJsonEdit.addDevDependencies(out.toString(), missing);
  }

  /**
   * Puts a file that breaks a lint rule in the folder Astro generates code into, so a clean check
   * proves the generated ESLint config ignores it.
   */
  static void writeLintViolationInAstroFolder(final Path project) throws IOException {
    Files.createDirectories(project.resolve(ASTRO_IGNORED_FILE).getParent());
    Files.writeString(project.resolve(ASTRO_IGNORED_FILE), ASTRO_IGNORED_SOURCE);
  }
}
