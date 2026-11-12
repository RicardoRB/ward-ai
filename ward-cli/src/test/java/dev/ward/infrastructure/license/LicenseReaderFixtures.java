package dev.ward.infrastructure.license;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyRelation;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Helpers for the license reader tests: a dependency, a project and files under a temp root. */
public final class LicenseReaderFixtures {
  private LicenseReaderFixtures() {}

  /** Returns a runtime dependency of {@code ecosystem}. */
  public static Dependency dependency(
      final Ecosystem ecosystem, final String name, final String version) {
    return new Dependency(
        new PackageId(ecosystem, name, version),
        DependencyRelation.DIRECT,
        DependencyScope.RUNTIME,
        true,
        "lock",
        "manifest",
        Set.of(),
        Set.of());
  }

  /** Returns a project rooted at {@code root}. */
  public static Project project(final Path root, final Language language, final BuildTool tool) {
    return new Project(root, language, tool, Framework.NONE);
  }

  /** Writes {@code content} to {@code relative} under {@code root}, creating directories. */
  public static Path write(final Path root, final String relative, final String content)
      throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
    return file;
  }
}
