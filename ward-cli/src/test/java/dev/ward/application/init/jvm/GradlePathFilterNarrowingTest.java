package dev.ward.application.init.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.PartialCheckDefaults;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.PartialCheckPlan;
import dev.ward.domain.config.PartialGateConfiguration;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Gradle Checkstyle path filter narrows nested edits to one {@code -PwardPaths} argument. */
class GradlePathFilterNarrowingTest {

  @Test
  void twoNestedFilesBecomeOneWardPathsArgumentWithSlashSeparators(@TempDir final Path root)
      throws IOException {
    final Path first = root.resolve("src/main/java/a/A.java");
    final Path second = root.resolve("src/main/java/b/B.java");
    Files.createDirectories(first.getParent());
    Files.createDirectories(second.getParent());
    Files.writeString(first, "class A {}\n");
    Files.writeString(second, "class B {}\n");
    final Project gradle = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    final PartialCheckDefaults.Table table =
        PartialCheckDefaults.forGate("checkstyle", gradle, QualityPreset.STANDARD, List.of())
            .orElseThrow();
    final GateConfiguration checkstyle =
        new GateConfiguration(
            "checkstyle",
            "checkstyle",
            true,
            List.of("./gradlew", "checkstyleMain"),
            Set.of("edit"),
            new PartialGateConfiguration(
                table.match(), table.full(), table.args(), table.item(), table.separator()),
            null,
            null,
            null,
            null);

    final PartialCheckPlan plan =
        PartialCheckPlan.create(
            root,
            Map.of("checkstyle", checkstyle),
            List.of(first, second),
            Files::exists,
            path -> Optional.empty());

    assertThat(plan.gates().get("checkstyle").scope()).isEqualTo(PartialCheckPlan.Scope.NARROW);
    assertThat(plan.gates().get("checkstyle").args())
        .containsExactly("-PwardPaths=src/main/java/a/A.java,src/main/java/b/B.java");
  }
}
