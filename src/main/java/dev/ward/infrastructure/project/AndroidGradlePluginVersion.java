package dev.ward.infrastructure.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Android Gradle Plugin version a project declares: from a plugin's {@code version} in a
 * build script, or from the version-catalog entry the plugin alias points at.
 */
public final class AndroidGradlePluginVersion {
  private static final Path CATALOG = Path.of("gradle", "libs.versions.toml");
  private static final Pattern PLUGIN_VERSION =
      Pattern.compile(
          "com\\.android\\.(?:application|library)[\"']\\s*\\)?\\s*version\\s*\\(?\\s*[\"']"
              + "([0-9][^\"']*)[\"']");
  private static final Pattern CATALOG_REF =
      Pattern.compile(
          "com\\.android\\.(?:application|library)\"\\s*,\\s*version\\.ref\\s*=\\s*\"([^\"]+)\"");

  private AndroidGradlePluginVersion() {}

  /** Returns the declared plugin version, or none when no build script or catalog declares one. */
  public static Optional<String> read(final Path root) {
    final Optional<String> direct =
        ProjectDirectories.below(root).stream()
            .map(GradleKotlinModules::scriptFile)
            .flatMap(Optional::stream)
            .map(AndroidGradlePluginVersion::text)
            .map(PLUGIN_VERSION::matcher)
            .filter(Matcher::find)
            .map(matcher -> matcher.group(1))
            .findFirst();
    return direct.isPresent() ? direct : fromCatalog(root.resolve(CATALOG));
  }

  /** Follows {@code version.ref} from the Android plugin entry to its [versions] value. */
  private static Optional<String> fromCatalog(final Path catalog) {
    if (!Files.isRegularFile(catalog)) {
      return Optional.empty();
    }
    final String toml = text(catalog);
    final Matcher ref = CATALOG_REF.matcher(toml);
    if (!ref.find()) {
      return Optional.empty();
    }
    final Matcher value =
        Pattern.compile(
                "^\\s*" + Pattern.quote(ref.group(1)) + "\\s*=\\s*\"([^\"]+)\"", Pattern.MULTILINE)
            .matcher(toml);
    return value.find() ? Optional.of(value.group(1)) : Optional.empty();
  }

  private static String text(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      return "";
    }
  }
}
