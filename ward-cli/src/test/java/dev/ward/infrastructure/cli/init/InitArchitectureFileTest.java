package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.ArchitectureFile;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import dev.ward.infrastructure.init.gradle.GradleToolConfigurator;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.ProjectRuleSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The {@code ward-architecture.toml} that init writes once for a chosen architecture style, and
 * leaves to the user afterwards.
 */
class InitArchitectureFileTest {
  private static final String FILE = "ward-architecture.toml";
  private static final String EDITED = "version = 1\n# mine\n";

  @TempDir Path project;
  private InitCommandFixture init;

  @BeforeEach
  void gradleProject() throws IOException {
    Files.writeString(project.resolve("build.gradle.kts"), "plugins { java }\n");
    Files.createDirectories(project.resolve("src/main/java/shop"));
    Files.writeString(
        project.resolve("src/main/java/shop/App.java"),
        "package shop;\nclass App {}\n"
    );
    init =
        InitCommandFixture.repository(
            project,
            new JvmBuildToolConfigurator(new PomToolConfigurator(), new GradleToolConfigurator())
        );
  }

  @Test
  void dryRunShowsTheFileAndWritesNothing() {
    final int exit = run("--architecture", "hexagonal", "--dry-run");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).containsPattern("create\\s+\\S*" + FILE);
    assertThat(project.resolve(FILE)).doesNotExist();
  }

  @ParameterizedTest
  @CsvSource(
      {
          "hexagonal, domain-is-innermost",
          "clean, entities-do-not-know-use-cases",
          "layered, lower-layers-do-not-call-controllers"
      }
  )
  void aChosenStyleWritesAFileTheGateAccepts(final String style, final String rule)
      throws IOException {
    final int exit = run("--architecture", style);

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(project.resolve(FILE)))
        .startsWith("#:schema ")
        .contains("for the " + style + " style", "allow-empty = true", "external = [");
    final ArchitectureFile file =
        new ProjectRuleSource(new ClasspathRuleSource(List.of()))
            .architecture(project, null)
            .orElseThrow();
    assertThat(file.problems()).isEmpty();
    assertThat(file.rules()).singleElement().satisfies(r -> assertThat(r.name()).isEqualTo(rule));
  }

  @Test
  void aSecondInitLeavesAnEditedFileAloneAndShowsItAsKept() throws IOException {
    assertThat(run("--architecture", "hexagonal")).as(init.output()).isEqualTo(ExitCodes.OK);
    Files.writeString(project.resolve(FILE), EDITED);

    final int preview = run("--architecture", "clean", "--overwrite", "--dry-run");
    final String plan = init.output();
    final int exit = run("--architecture", "clean", "--overwrite");

    assertThat(preview).as(plan).isEqualTo(ExitCodes.OK);
    assertThat(plan).containsPattern("keep\\s+\\S*" + FILE);
    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(project.resolve(FILE))).isEqualTo(EDITED);
  }

  @Test
  void aSecondInitLeavesTheUneditedFileAloneToo() throws IOException {
    assertThat(run("--architecture", "hexagonal")).as(init.output()).isEqualTo(ExitCodes.OK);
    final String written = Files.readString(project.resolve(FILE));

    assertThat(run("--architecture", "clean", "--overwrite"))
        .as(init.output())
        .isEqualTo(ExitCodes.OK);

    assertThat(Files.readString(project.resolve(FILE))).isEqualTo(written);
  }

  @Test
  void aDefaultStyleWritesNoFile() {
    final int exit = init.run("", "--preset", "strict", "--integration", "none", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(project.resolve("ward.toml")).exists();
    assertThat(project.resolve(FILE)).doesNotExist();
  }

  private int run(final String... options) {
    return init.run(
        "",
        Stream.concat(
            Stream.of("--preset", "strict", "--integration", "none", "--yes"),
            Arrays.stream(options)
        )
            .toArray(String[]::new)
    );
  }
}
