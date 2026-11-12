package dev.ward.infrastructure.license.jvm;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.license.DeclaredLicense;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import dev.ward.infrastructure.license.jvm.PomDocument.Coordinates;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads the {@code <licenses>} of a Maven or Gradle package's POM from the local Maven repository
 * or the Gradle module cache, never from the network. A POM without {@code <licenses>} inherits
 * those of its {@code <parent>}, to a depth of {@value #MAX_PARENTS}, with a guard against cycles.
 * Properties in names and URLs are not interpolated.
 */
public final class PomLicenseMetadata implements LicenseMetadata {
  static final int MAX_PARENTS = 10;
  private static final String POM_SUFFIX = ".pom";

  private final Path mavenRepository;
  private final Path gradleCache;

  /**
   * Creates the reader.
   *
   * @param mavenRepository the local Maven repository, normally {@code ~/.m2/repository}
   * @param gradleCache the Gradle module cache, normally {@code
   *     ~/.gradle/caches/modules-2/files-2.1}
   */
  public PomLicenseMetadata(final Path mavenRepository, final Path gradleCache) {
    this.mavenRepository = mavenRepository;
    this.gradleCache = gradleCache;
  }

  @Override
  public LicenseLookup lookup(
      final Project project, final String modulePath, final Dependency dependency) {
    final String[] parts = dependency.id().name().split(":", 2);
    final Coordinates start =
        new Coordinates(parts[0], parts[parts.length - 1], dependency.id().version());
    return inherit(start, new HashSet<>(), 0, project.root().resolve(modulePath));
  }

  private LicenseLookup inherit(
      final Coordinates coordinates,
      final Set<Coordinates> seen,
      final int depth,
      final Path member) {
    if (depth > MAX_PARENTS || !seen.add(coordinates)) {
      return LicenseLookup.missing(
          LicenseLookup.Reason.NO_LICENSE_DECLARED, "the parent POM chain is too deep or cyclic");
    }
    final Optional<PomDocument> pom = find(coordinates, member);
    if (pom.isEmpty()) {
      return LicenseLookup.missing(
          LicenseLookup.Reason.NOT_INSTALLED,
          "POM of "
              + coordinates
              + " is in neither the local Maven repository nor the Gradle cache");
    }
    final List<DeclaredLicense.Entry> licenses = pom.get().licenses();
    if (!licenses.isEmpty()) {
      return LicenseLookup.found(new DeclaredLicense(licenses), "pom");
    }
    final Optional<Coordinates> parent = pom.get().parent();
    return parent.isPresent()
        ? inherit(parent.get(), seen, depth + 1, member)
        : LicenseLookup.missing(
            LicenseLookup.Reason.NO_LICENSE_DECLARED, "the POM declares no license");
  }

  private Optional<PomDocument> find(final Coordinates coordinates, final Path member) {
    final Path file =
        mavenRepository
            .resolve(coordinates.group().replace('.', '/'))
            .resolve(coordinates.artifact())
            .resolve(coordinates.version())
            .resolve(coordinates.artifact() + "-" + coordinates.version() + POM_SUFFIX);
    final Optional<PomDocument> local =
        Files.isRegularFile(file) ? PomDocument.parse(file) : Optional.empty();
    if (local.isPresent()) {
      return local;
    }
    final Optional<PomDocument> gradle = fromGradle(coordinates);
    return gradle.isPresent() ? gradle : ownPom(coordinates, member);
  }

  private Optional<PomDocument> fromGradle(final Coordinates coordinates) {
    final Path directory =
        gradleCache
            .resolve(coordinates.group())
            .resolve(coordinates.artifact())
            .resolve(coordinates.version());
    if (!Files.isDirectory(directory)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.walk(directory, 2)) {
      return files
          .filter(path -> LockFiles.fileName(path).endsWith(POM_SUFFIX))
          .sorted()
          .findFirst()
          .flatMap(PomDocument::parse);
    } catch (IOException exception) {
      return Optional.empty();
    }
  }

  /** A package built in the project reads the POM of its member directory. */
  private static Optional<PomDocument> ownPom(final Coordinates coordinates, final Path member) {
    final Path file = member.resolve("pom.xml");
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    return PomDocument.parse(file)
        .filter(
            pom ->
                coordinates.artifact().equals(pom.artifactId())
                    && coordinates.version().equals(pom.version()));
  }
}
