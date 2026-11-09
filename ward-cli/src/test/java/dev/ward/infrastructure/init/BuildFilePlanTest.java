package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.PlannedFileChange.Action;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Build-file plans list each added or updated tool and never touch the file. */
class BuildFilePlanTest {
  private static final String POM = "pom.xml";
  private static final String EMPTY_POM = "<project><modelVersion>4.0.0</modelVersion></project>";

  @Test
  void mavenPlanListsAddedPluginsWithVersionsAndWritesNothing(@TempDir final Path root)
      throws IOException {
    Files.writeString(root.resolve(POM), EMPTY_POM);

    final PlannedFileChange plan =
        new PomToolConfigurator().plan(maven(root), gates("coverage", "mutation"), null, false);

    assertThat(plan.file()).isEqualTo(root.resolve(POM));
    assertThat(plan.action()).isEqualTo(Action.MODIFY);
    assertThat(plan.details())
        .anyMatch(detail -> detail.matches("\\+ jacoco-maven-plugin \\d+\\.\\d+\\.\\d+"))
        .anyMatch(detail -> detail.matches("\\+ pitest-maven \\d+[.\\d]*"));
    assertThat(Files.readString(root.resolve(POM))).isEqualTo(EMPTY_POM);
  }

  @Test
  void mavenPlanIsUnchangedWhenToolsAreDeclared(@TempDir final Path root) throws IOException {
    Files.writeString(root.resolve(POM), EMPTY_POM);
    final PomToolConfigurator configurator = new PomToolConfigurator();
    configurator.apply(maven(root), gates("coverage"));

    final PlannedFileChange plan = configurator.plan(maven(root), gates("coverage"), null, false);

    assertThat(plan.action()).isEqualTo(Action.UNCHANGED);
    assertThat(plan.details()).isEmpty();
  }

  private static Project maven(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("mvn", id), true, "available"))
        .toList();
  }
}
