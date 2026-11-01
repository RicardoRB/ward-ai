package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Module placement in a Maven aggregator: each Java module declares the module-placed gates, and
 * the aggregator pins their versions in pluginManagement.
 */
class MavenModuleConfiguratorTest {
  private static final String JACOCO = "jacoco-maven-plugin";
  private static final String POM = "pom.xml";
  private static final String COVERAGE = "coverage";
  private static final String ROOT_POM =
      "<project>\n"
          + "  <modelVersion>4.0.0</modelVersion>\n"
          + "  <packaging>pom</packaging>\n"
          + "  <modules>\n"
          + "    <module>api</module>\n"
          + "    <module>domain</module>\n"
          + "    <module>config</module>\n"
          + "  </modules>\n"
          + "</project>\n";
  private static final String MODULE_POM =
      "<project>\n  <modelVersion>4.0.0</modelVersion>\n</project>\n";

  @TempDir Path root;

  @Test
  void placesCoverageInEachJavaModuleAndPinsItsVersionInTheAggregator() throws IOException {
    final Project project = aggregator();

    new PomToolConfigurator().apply(project, ModuleFixtures.gates(COVERAGE));

    assertThat(read("api/pom.xml")).contains(JACOCO, "<rules>");
    assertThat(read("domain/pom.xml")).contains(JACOCO, "<rules>");
    assertThat(read(POM)).contains("<pluginManagement>", JACOCO, "<version>0.8.13</version>");
    assertThat(PomXml.hasProjectPlugin(read(POM), JACOCO)).isFalse();
  }

  @Test
  void leavesAModuleWithoutJavaSourcesUntouched() throws IOException {
    final Project project = aggregator();

    new PomToolConfigurator().apply(project, ModuleFixtures.gates(COVERAGE));

    assertThat(read("config/pom.xml")).isEqualTo(MODULE_POM);
  }

  @Test
  void singleModuleProjectKeepsTheRootPlugin() throws IOException {
    Files.writeString(root.resolve(POM), MODULE_POM);

    new PomToolConfigurator().apply(project(), ModuleFixtures.gates(COVERAGE));

    assertThat(PomXml.hasProjectPlugin(read(POM), JACOCO)).isTrue();
  }

  @Test
  void rollbackRestoresEveryPomInTheRun() throws IOException {
    final Project project = aggregator();

    final BuildFileChange change =
        new PomToolConfigurator().apply(project, ModuleFixtures.gates(COVERAGE));
    new PomToolConfigurator().rollback(change);

    assertThat(read(POM)).isEqualTo(ROOT_POM);
    assertThat(read("api/pom.xml")).isEqualTo(MODULE_POM);
    assertThat(read("domain/pom.xml")).isEqualTo(MODULE_POM);
  }

  @Test
  void failedModuleWriteRestoresEarlierPoms() throws IOException {
    final Project project = aggregator();
    final Path domain = root.resolve("domain/pom.xml");
    domain.toFile().setWritable(false);
    assumeFalse(Files.isWritable(domain), "read-only files are writable for this user");

    assertThatThrownBy(
            () -> new PomToolConfigurator().apply(project, ModuleFixtures.gates(COVERAGE)))
        .isInstanceOf(WardException.class);

    assertThat(read(POM)).isEqualTo(ROOT_POM);
    assertThat(read("api/pom.xml")).isEqualTo(MODULE_POM);
    domain.toFile().setWritable(true);
  }

  @Test
  void moduleRuleFilesResolveFromTheAggregator() throws IOException {
    new PomToolConfigurator()
        .apply(aggregator(), ModuleFixtures.gates("checkstyle"), QualityPreset.STANDARD);

    assertThat(read("api/pom.xml"))
        .contains("${maven.multiModuleProjectDirectory}/config/checkstyle.xml")
        .doesNotContain("${project.basedir}/config/checkstyle.xml");
  }

  @Test
  void moduleGatesAreTheSameAsTheGradleModuleGates() {
    final List<String> maven =
        PomTools.TOOLS.stream().filter(PomTools.Tool::module).map(PomTools.Tool::gate).toList();

    assertThat(maven)
        .containsExactlyInAnyOrder(
            "checkstyle", "pmd", "format", "enforcer", "spotbugs", "coverage", "mutation");
    assertThat(maven).containsExactlyInAnyOrderElementsOf(GradleBuildBlocks.moduleGates());
  }

  /** A pom aggregator with Java modules api and domain, and a resources-only module config. */
  private Project aggregator() throws IOException {
    Files.writeString(root.resolve(POM), ROOT_POM);
    for (final String module : new String[] {"api", "domain"}) {
      Files.createDirectories(root.resolve(module).resolve("src/main/java"));
      Files.writeString(root.resolve(module).resolve(POM), MODULE_POM);
    }
    Files.createDirectories(root.resolve("config/src/main/resources"));
    Files.writeString(root.resolve("config/pom.xml"), MODULE_POM);
    return project();
  }

  private Project project() {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private String read(final String name) throws IOException {
    return Files.readString(root.resolve(name));
  }
}
