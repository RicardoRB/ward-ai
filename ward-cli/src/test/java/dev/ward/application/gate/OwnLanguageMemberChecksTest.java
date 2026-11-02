package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A member's findings name the member's path below the Turborepo root. */
class OwnLanguageMemberChecksTest {
  private static final Path ROOT = Path.of("/repo");
  private static final String FILE = "src/main/java/dev/codeja/Application.java";

  @Test
  void memberFindingIsRebasedOntoTheRepositoryRoot() {
    final Finding finding =
        new Finding(
            "checkstyle",
            "checkstyle",
            "JavadocType",
            FindingSeverity.WARNING,
            "Missing a Javadoc comment.",
            FILE,
            1,
            null,
            null);
    final CheckReport member =
        new CheckReport(
            new Project(ROOT.resolve("apps/api"), Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            List.of(
                GateResult.builder("checkstyle", GateStatus.FAILED)
                    .build()
                    .withFindings(GateFindings.of(List.of(finding)))));

    final CheckReport merged =
        OwnLanguageMemberChecks.merge(new CheckReport(turboRoot(), List.of()), List.of(member));

    assertThat(merged.results())
        .extracting(GateResult::name)
        .containsExactly("apps/api: checkstyle");
    assertThat(merged.results().getFirst().findings().items())
        .extracting(Finding::file)
        .containsExactly("apps/api/" + FILE);
  }

  private static Project turboRoot() {
    return new Project(ROOT, Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE)
        .withTaskRunner(TaskRunner.TURBO);
  }
}
