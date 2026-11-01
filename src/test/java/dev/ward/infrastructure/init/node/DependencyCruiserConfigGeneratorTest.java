package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.project.FileSystemNodeToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Writes, reuses, rewrites and rolls back the dependency-cruiser config of a Node project. */
class DependencyCruiserConfigGeneratorTest {
  private static final String FILE = ".dependency-cruiser.mjs";
  private static final String LAYERED = "layered";
  private static final QualityPreset.ArchitectureExtras STANDARD =
      new QualityPreset.ArchitectureExtras(false, false, false);
  private static final QualityPreset.ArchitectureExtras STRICT =
      new QualityPreset.ArchitectureExtras(true, true, true);

  @TempDir Path root;

  private final DependencyCruiserConfigGenerator generator =
      new DependencyCruiserConfigGenerator(new FileSystemNodeToolConfigs());

  @Test
  void createsMarkedConfigAndRollbackDeletesIt() throws IOException {
    assertThat(generator.plan(project(), LAYERED, STANDARD).action())
        .isEqualTo(PlannedFileChange.Action.CREATE);

    final ArchitectureTestChange change = generator.apply(project(), LAYERED, STANDARD);

    assertThat(change.created()).isTrue();
    assertThat(Files.readString(root.resolve(FILE)))
        .isEqualTo(DependencyCruiserConfigSource.render(LAYERED, STANDARD, true, false));
    generator.rollback(change);
    assertThat(root.resolve(FILE)).doesNotExist();
  }

  @Test
  void rerunWithTheSameStyleLeavesTheFileUnchanged() throws IOException {
    generator.apply(project(), LAYERED, STANDARD);

    final ArchitectureTestChange again = generator.apply(project(), LAYERED, STANDARD);

    assertThat(again.created()).isFalse();
    assertThat(again.previous()).isNull();
    assertThat(generator.plan(project(), LAYERED, STANDARD).action())
        .isEqualTo(PlannedFileChange.Action.UNCHANGED);
  }

  @Test
  void styleChangeRewritesWardsConfigAndRollbackRestoresIt() throws IOException {
    generator.apply(project(), LAYERED, STANDARD);
    final String layered = Files.readString(root.resolve(FILE));

    assertThat(generator.plan(project(), "clean", STRICT).action())
        .isEqualTo(PlannedFileChange.Action.MODIFY);
    final ArchitectureTestChange change = generator.apply(project(), "clean", STRICT);

    assertThat(change.previous()).isEqualTo(layered);
    assertThat(Files.readString(root.resolve(FILE))).contains("clean-architecture", "no-circular");
    generator.rollback(change);
    assertThat(Files.readString(root.resolve(FILE))).isEqualTo(layered);
  }

  @Test
  void projectsOwnConfigIsKeptAndNothingIsWritten() throws IOException {
    Files.writeString(root.resolve(".dependency-cruiser.cjs"), "module.exports = {};\n");

    assertThat(generator.plan(project(), LAYERED, STANDARD))
        .isEqualTo(
            new PlannedFileChange(
                root.resolve(".dependency-cruiser.cjs"),
                PlannedFileChange.Action.PRESERVE_USER_OWNED));
    final ArchitectureTestChange change = generator.apply(project(), LAYERED, STANDARD);

    assertThat(change.file()).isEqualTo(root.resolve(".dependency-cruiser.cjs"));
    assertThat(change.created()).isFalse();
    assertThat(root.resolve(FILE)).doesNotExist();
  }

  @Test
  void noArchUnitTestsExistInNodeProjects() {
    assertThat(generator.existingArchUnitTests(project())).isEmpty();
  }

  private Project project() {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        "24",
        TaskRunner.NONE,
        null);
  }
}
