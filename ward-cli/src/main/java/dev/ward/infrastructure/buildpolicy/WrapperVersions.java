package dev.ward.infrastructure.buildpolicy;

import dev.ward.domain.buildpolicy.BuildFileException;
import dev.ward.domain.buildpolicy.ToolVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the build tool version a wrapper properties file declares in its distribution URL. */
public final class WrapperVersions {
  private static final String KEY = "distributionUrl";

  private WrapperVersions() {}

  /**
   * Returns the version of {@code tool} that {@code properties} declares, or empty when the file
   * does not exist.
   *
   * @param distribution the file-name pattern before the version, such as {@code apache-maven-}
   * @throws BuildFileException if the URL names no version
   */
  public static Optional<ToolVersion> read(
      final Path properties, final String shown, final String tool, final String distribution) {
    if (!Files.isRegularFile(properties)) {
      return Optional.empty();
    }
    final String text = BuildPaths.read(properties, shown);
    final Matcher url =
        Pattern.compile("(?m)^\\s*" + KEY + "\\s*[=:]\\s*(\\S+)").matcher(text.replace("\\:", ":"));
    if (!url.find()) {
      throw new BuildFileException(shown, "no " + KEY + " names a version");
    }
    final Matcher version =
        Pattern.compile(Pattern.quote(distribution) + "(\\d+(?:\\.\\d+)*)").matcher(url.group(1));
    if (!version.find()) {
      throw new BuildFileException(shown, KEY + " names no version");
    }
    return Optional.of(
        new ToolVersion(tool, version.group(1), shown, BuildPaths.lineOf(text, KEY)));
  }
}
