package dev.ward.infrastructure.license.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.license.LicenseService;
import dev.ward.config.DaggerWardComponent;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.license.BundledLicenseCatalog;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compares the declared licenses Ward reads for a Gradle project with the listing of the Gradle
 * license report plugin ({@code com.github.jk1.dependency-license-report}).
 *
 * <p>The plugin ran once; its names are committed in {@code expected.txt}, as for the Maven
 * counterpart {@link PomLicenseParitySmokeTest}, so the plugin need not stay. Ward reads the POMs
 * from the Gradle module cache, which {@code gradle dependencies} fills first, so the run needs the
 * network once. Run with {@code -Dward.license.parity.gradle=<gradle executable>}.
 */
@EnabledIfSystemProperty(named = "ward.license.parity.gradle", matches = ".+")
class PomLicenseGradleParitySmokeTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/license-parity/gradle");
  private static final long TIMEOUT_MINUTES = 10;

  @TempDir Path work;

  @Test
  void declaredLicensesMatchTheGradlePluginsListing() throws Exception {
    InventoryFixtures.copy(FIXTURE.toAbsolutePath(), work);
    fillTheModuleCache();
    final Path home = Path.of(System.getProperty("user.home"));
    final LicenseService service =
        new LicenseService(
            DaggerWardComponent.create().dependencyInventoryService(),
            Map.of(
                Ecosystem.MAVEN,
                new PomLicenseMetadata(
                    home.resolve(".m2/repository"),
                    home.resolve(".gradle/caches/modules-2/files-2.1"))),
            new BundledLicenseCatalog());

    final Set<String> actual =
        service
            .licenses(new Project(work, Language.JAVA, BuildTool.GRADLE, Framework.NONE), Set.of())
            .stream()
            .map(PomLicenseParitySmokeTest::line)
            .collect(Collectors.toCollection(TreeSet::new));

    assertThat(actual).isEqualTo(PomLicenseParitySmokeTest.expected(FIXTURE));
  }

  /** Resolves the runtime classpath, which downloads the POMs into the Gradle module cache. */
  private void fillTheModuleCache() throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(
                System.getProperty("ward.license.parity.gradle"),
                "--no-daemon",
                "-q",
                "dependencies",
                "--configuration",
                "runtimeClasspath")
            .directory(work.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    assertThat(process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)).isTrue();
    assertThat(process.exitValue()).isZero();
  }
}
