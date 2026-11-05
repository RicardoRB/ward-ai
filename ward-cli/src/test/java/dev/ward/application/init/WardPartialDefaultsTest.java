package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The {@code paths} table init writes for the {@code ward} check. */
class WardPartialDefaultsTest {
  private static final Project JAVA_PROJECT =
      new Project(Path.of("/repo"), Language.JAVA, BuildTool.GRADLE, Framework.NONE);

  @Test
  void wardCheckGetsASourceMatchAndRunsInFullForItsConfigurationFiles() {
    final PartialCheckDefaults.Table table =
        PartialCheckDefaults.forGate("ward", JAVA_PROJECT, null, List.of()).orElseThrow();

    assertThat(table.match()).contains("**/*.java", "**/*.kt", "**/*.ts");
    assertThat(table.full()).contains("ward.toml", "build.gradle*");
    assertThat(table.args()).isEmpty();
  }
}
