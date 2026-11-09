package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.ProjectFiles;
import java.util.List;

/**
 * The project a fixture runs in beyond its own file: the manifests, and the other source files that
 * give cross-file facts but report nothing.
 *
 * @param manifests the manifest files of the project
 * @param sources the other source files of the project
 */
record FixtureProject(ProjectFiles manifests, List<SourceUnit> sources) {

  /** The project of a fixture with no other files. */
  static FixtureProject alone() {
    return new FixtureProject(ProjectFiles.empty(), List.of());
  }
}
