package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Android modules get the Android gate blocks, and never a JVM plugin's blocks. */
class GradleAndroidModulePlacementTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/android-project");
  private static final String APP = "app/build.gradle.kts";
  private static final String CORE = "core/build.gradle.kts";
  private static final String COVERAGE_BLOCK = "enableUnitTestCoverage = true";

  @TempDir Path root;

  @Test
  void appModuleReceivesTheUnitTestCoverageBlock() throws IOException {
    final Project project = copy();

    new GradleToolConfigurator().apply(project, gates("android-coverage"), null, finding -> false);

    assertThat(read(APP)).contains(COVERAGE_BLOCK, "getByName(\"debug\")");
    assertThat(read(CORE)).contains(COVERAGE_BLOCK);
  }

  @Test
  void androidModuleStaysFreeOfJvmPluginBlocks() throws IOException {
    final Project project = copy();

    new GradleToolConfigurator()
        .apply(
            project,
            gates("android-coverage", "checkstyle", "pmd", "spotbugs", "coverage"),
            null,
            finding -> false);

    assertThat(read(APP))
        .contains(COVERAGE_BLOCK)
        .doesNotContain("checkstyle", "pmd", "spotbugs", "jacoco", "com.github.spotbugs");
    assertThat(read(CORE)).doesNotContain("checkstyle", "pmd", "spotbugs", "jacoco");
  }

  @Test
  void rerunChangesNoAndroidScript() throws IOException {
    final Project project = copy();
    final GradleToolConfigurator configurator = new GradleToolConfigurator();
    final List<InitGateOption> gates = gates("android-coverage");
    configurator.apply(project, gates, null, finding -> false);
    final String app = read(APP);

    final BuildFileChange second = configurator.apply(project, gates, null, finding -> false);

    assertThat(second.moduleScripts()).isEmpty();
    assertThat(read(APP)).isEqualTo(app);
  }

  @Test
  void rollbackRestoresTheAndroidModuleScripts() throws IOException {
    final Project project = copy();
    final GradleToolConfigurator configurator = new GradleToolConfigurator();
    final String app = read(APP);
    final String core = read(CORE);

    final BuildFileChange change =
        configurator.apply(project, gates("android-coverage"), null, finding -> false);
    configurator.rollback(change);

    assertThat(read(APP)).isEqualTo(app);
    assertThat(read(CORE)).isEqualTo(core);
  }

  @Test
  void failedAndroidModuleWriteLeavesEveryScriptAsBefore() throws IOException {
    final Project project = copy();
    final Path core = root.resolve(CORE);
    final String app = read(APP);
    final String coreOriginal = read(CORE);
    core.toFile().setWritable(false);
    assumeFalse(Files.isWritable(core), "read-only files are writable for this user");

    assertThatThrownBy(
            () ->
                new GradleToolConfigurator()
                    .apply(project, gates("android-coverage"), null, finding -> false))
        .isInstanceOf(WardException.class);

    assertThat(read(APP)).isEqualTo(app);
    assertThat(read(CORE)).isEqualTo(coreOriginal);
    core.toFile().setWritable(true);
  }

  private Project copy() throws IOException {
    try (Stream<Path> files = Files.walk(FIXTURE)) {
      for (final Path source : files.toList()) {
        final Path target = root.resolve(FIXTURE.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
      }
    }
    return new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.ANDROID);
  }

  private String read(final String name) throws IOException {
    return Files.readString(root.resolve(name));
  }

  private static List<InitGateOption> gates(final String... ids) {
    return ModuleFixtures.gates(ids);
  }
}
