package dev.ward.infrastructure.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the Gradle version the wrapper pins in its distribution URL. */
public final class GradleWrapperVersion {
  private static final Path PROPERTIES = Path.of("gradle", "wrapper", "gradle-wrapper.properties");
  private static final Pattern DISTRIBUTION =
      Pattern.compile("gradle-([0-9][^/\\\\\\s]*?)-(?:bin|all)\\.zip");

  private GradleWrapperVersion() {}

  /** Returns the pinned Gradle version, or none when the wrapper properties name none. */
  public static Optional<String> read(final Path root) {
    final Path file = root.resolve(PROPERTIES);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      final Matcher matcher = DISTRIBUTION.matcher(Files.readString(file));
      return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
