package dev.ward.infrastructure.license.swift;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.license.DeclaredLicense;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.license.LicenseTexts;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads a Swift package's license from the license file in its checkout, because {@code
 * Package.swift} declares none: {@code LICENSE}, {@code LICENSE.md}, {@code LICENSE.txt} or {@code
 * COPYING} in {@code .build/checkouts/<name>} or {@code SourcePackages/checkouts/<name>} under the
 * member directory or the project root. The text is matched against the bundled license texts; a
 * text that matches none is unrecognized.
 */
public final class SwiftLicenseMetadata implements LicenseMetadata {
  private static final List<String> FILES =
      List.of("LICENSE", "LICENSE.md", "LICENSE.txt", "COPYING");
  private static final List<String> CHECKOUTS =
      List.of(".build/checkouts", "SourcePackages/checkouts");

  private final SwiftLicenseMatcher matcher;

  /** Creates the reader over the bundled license texts. */
  public SwiftLicenseMetadata(final LicenseTexts texts) {
    this.matcher = new SwiftLicenseMatcher(texts);
  }

  @Override
  public LicenseLookup lookup(
      final Project project, final String modulePath, final Dependency dependency) {
    final String name = dependency.id().name();
    final String repository = name.substring(name.lastIndexOf('/') + 1);
    final Optional<Path> checkout = checkout(project.root(), modulePath, repository);
    if (checkout.isEmpty()) {
      return LicenseLookup.missing(
          LicenseLookup.Reason.NOT_INSTALLED, repository + " has no local checkout");
    }
    for (final String file : FILES) {
      final Path path = checkout.get().resolve(file);
      if (Files.isRegularFile(path)) {
        return read(path, file);
      }
    }
    return LicenseLookup.missing(
        LicenseLookup.Reason.NO_LICENSE_DECLARED, "the checkout has no license file");
  }

  private LicenseLookup read(final Path path, final String file) {
    try {
      return matcher
          .match(Files.readString(path))
          .map(id -> LicenseLookup.found(DeclaredLicense.of(id), "license-file"))
          .orElseGet(
              () ->
                  LicenseLookup.missing(
                      LicenseLookup.Reason.UNRECOGNIZED,
                      "the text of " + file + " matches no bundled license"));
    } catch (IOException exception) {
      return LicenseLookup.missing(LicenseLookup.Reason.UNRECOGNIZED, file + " is unreadable");
    }
  }

  private static Optional<Path> checkout(
      final Path root, final String modulePath, final String repository) {
    for (final Path base : List.of(root.resolve(modulePath), root)) {
      for (final String checkouts : CHECKOUTS) {
        final Optional<Path> found = named(base.resolve(checkouts), repository);
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return Optional.empty();
  }

  /** Finds the checkout directory called {@code repository}, ignoring case. */
  private static Optional<Path> named(final Path directory, final String repository) {
    if (!Files.isDirectory(directory)) {
      return Optional.empty();
    }
    try (Stream<Path> children = Files.list(directory)) {
      return children
          .filter(Files::isDirectory)
          .filter(path -> LockFiles.fileName(path).toLowerCase(Locale.ROOT).equals(repository))
          .sorted()
          .findFirst();
    } catch (IOException exception) {
      return Optional.empty();
    }
  }
}
