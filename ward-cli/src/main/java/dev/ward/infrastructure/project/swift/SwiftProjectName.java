package dev.ward.infrastructure.project.swift;

import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectNameReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Reads the package name of {@code Package.swift}; an Xcode project declares none here. */
public final class SwiftProjectName implements ProjectNameReader {
  private static final Pattern NAME = Pattern.compile("Package\\s*\\(\\s*name:\\s*\"([^\"]+)\"");

  /** Creates the reader. */
  @Inject
  public SwiftProjectName() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public Optional<String> read(final Project project) {
    try {
      final Matcher matcher =
          NAME.matcher(Files.readString(project.root().resolve("Package.swift")));
      return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    } catch (IOException exception) {
      return Optional.empty();
    }
  }
}
