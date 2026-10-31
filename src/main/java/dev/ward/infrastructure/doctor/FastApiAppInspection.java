package dev.ward.infrastructure.doctor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the FastAPI application a project defines, as {@code module:attribute}. */
final class FastApiAppInspection {
  // The files a FastAPI project keeps its application in, the first match wins.
  private static final List<Path> FASTAPI_FILES =
      List.of(
          Path.of("main.py"),
          Path.of("app.py"),
          Path.of("app", "main.py"),
          Path.of("app", "app.py"));
  private static final Pattern FASTAPI_ASSIGNMENT =
      Pattern.compile("(?m)^(\\w+)\\s*=\\s*FastAPI\\(");

  private FastApiAppInspection() {}

  /** Returns the first application found in the candidate files, or empty when there is none. */
  static Optional<String> find(final Path projectRoot) {
    return FASTAPI_FILES.stream()
        .map(projectRoot::resolve)
        .filter(Files::isRegularFile)
        .flatMap(file -> fastApiAttribute(projectRoot, file).stream())
        .findFirst();
  }

  /** Returns {@code module:attribute} when {@code file} assigns a {@code FastAPI(} instance. */
  private static Optional<String> fastApiAttribute(final Path projectRoot, final Path file) {
    try {
      final Matcher app = FASTAPI_ASSIGNMENT.matcher(Files.readString(file));
      if (!app.find()) {
        return Optional.empty();
      }
      final String module =
          projectRoot.relativize(file).toString().replaceAll("\\.py$", "").replace('/', '.');
      return Optional.of(module + ":" + app.group(1));
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
