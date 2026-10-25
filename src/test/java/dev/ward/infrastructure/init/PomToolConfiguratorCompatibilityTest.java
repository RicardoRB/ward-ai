package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.CompatibilityProjectSnapshot;
import dev.ward.domain.init.GateOmission;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.ToolchainSnapshot;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomToolConfiguratorCompatibilityTest {
  private static final String POM = "pom.xml";
  private static final int JAVA_17 = 17;
  // Older than every managed plugin supports.
  private static final int JAVA_7 = 7;

  @Test
  void selectsCatalogVersionForMissingPlugin(@TempDir final Path root) throws IOException {
    writePom(root, "<project><build/></project>");
    final Project project = project(root);
    final PomToolConfigurator configurator =
        new PomToolConfigurator(ignored -> snapshot(JAVA_17, Map.of()));

    configurator.apply(project, List.of(gate("format")), null, finding -> true);

    assertThat(Files.readString(root.resolve(POM))).contains("<version>3.10.3</version>");
  }

  @Test
  void asksBeforeUpdatingIncompatiblePluginAndPreservesConfiguration(@TempDir final Path root)
      throws IOException {
    writePom(
        root,
        "<project><build><plugins><plugin><artifactId>spotless-maven-plugin</artifactId>"
            + "<version>1.0.0</version><configuration><java>mine</java></configuration>"
            + "</plugin></plugins></build></project>");
    final Project project = project(root);
    final PomToolConfigurator configurator =
        new PomToolConfigurator(
            ignored -> snapshot(JAVA_17, Map.of("spotless-maven-plugin", "1.0.0")));
    final String original = Files.readString(root.resolve(POM));

    final BuildFileChange change =
        configurator.apply(project, List.of(gate("format")), null, ignored -> false);

    assertThat(Files.readString(root.resolve(POM))).isEqualTo(original);
    assertThat(change.warnings()).anyMatch(message -> message.contains("declined"));
  }

  @Test
  void omitsAGateWithNoVersionForTheRuntimeJdkWithoutWriting(@TempDir final Path root)
      throws IOException {
    writePom(root, "<project><build/></project>");
    final Project project = project(root);
    final PomToolConfigurator configurator =
        new PomToolConfigurator(ignored -> snapshot(JAVA_7, Map.of()));
    final String original = Files.readString(root.resolve(POM));

    final BuildFileChange change =
        configurator.apply(project, List.of(gate("format")), null, ignored -> true);

    assertThat(Files.readString(root.resolve(POM))).isEqualTo(original);
    assertThat(change.warnings())
        .containsExactly(
            "Ward supports Java 8 to 25; the project builds on JDK 7, and Ward may not work with"
                + " it.",
            "format omitted: spotless-maven-plugin needs JDK 8+; this project builds on JDK 7");
    assertThat(configurator.omissions(project, List.of("format", "pmd")))
        .extracting(GateOmission::gate)
        .containsExactly("pmd", "format");
  }

  static CompatibilityProjectSnapshot snapshot(
      final int javaVersion, final Map<String, String> plugins) {
    return new CompatibilityProjectSnapshot(
        new Project(Path.of("."), Language.JAVA, BuildTool.MAVEN, Framework.NONE),
        new ToolchainSnapshot(
            Optional.of(javaVersion), Optional.empty(), Optional.of("3.9.0"), Framework.NONE),
        plugins);
  }

  static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }

  static void writePom(final Path root, final String content) throws IOException {
    Files.writeString(root.resolve(POM), content);
  }
}
