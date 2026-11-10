package dev.ward.infrastructure.init.maven;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.maven.MavenJavaModules;
import java.nio.file.Files;

/**
 * Finds where a Maven aggregator's Java sources live, so architecture rules are generated in the
 * module that owns them rather than in a parent that has no sources.
 */
public final class MavenCodeRoot {
  private MavenCodeRoot() {}

  /**
   * Returns the project rooted at the first module with Java sources when the aggregator has none
   * of its own, otherwise {@code project} unchanged.
   */
  public static Project of(final Project project) {
    if (project.buildTool() != BuildTool.MAVEN
        || Files.isDirectory(project.root().resolve("src/main/java"))) {
      return project;
    }
    return MavenJavaModules.of(project.root()).stream()
        .findFirst()
        .map(
            pom ->
                new Project(
                    pom.getParent(), project.language(), BuildTool.MAVEN, project.framework()))
        .orElse(project);
  }
}
