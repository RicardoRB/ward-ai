package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.gate.QualityGateDefinitions;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gates that were removed and must not come back: {@code enforcer}, whose rules are the native
 * {@code ward:build/dependency-convergence} and {@code ward:build/require-upper-bound}.
 */
class RemovedGatesCatalogTest {
  private static final Set<String> REMOVED_GATES = Set.of("enforcer");

  @TempDir Path root;

  @Test
  void removedGatesAreNeitherDefinedNorOffered() {
    final List<String> offered = offered();
    for (final String removed : REMOVED_GATES) {
      assertThat(QualityGateDefinitions.find(removed)).as("definition of %s", removed).isEmpty();
      assertThat(offered).as("gates offered, without %s", removed).doesNotContain(removed);
    }
  }

  private List<String> offered() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(new FakeEnvironmentInspection());
    return Stream.of(BuildTool.MAVEN, BuildTool.GRADLE)
        .map(buildTool -> new Project(root, Language.JAVA, buildTool, Framework.NONE))
        .flatMap(project -> catalog.gates(project, null).stream())
        .map(InitGateOption::id)
        .toList();
  }
}
