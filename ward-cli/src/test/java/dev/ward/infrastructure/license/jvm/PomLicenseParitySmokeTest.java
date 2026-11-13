package dev.ward.infrastructure.license.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.dependency.DependencyInventoryService;
import dev.ward.application.license.LicenseService;
import dev.ward.application.license.PackageLicense;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.license.DeclaredLicense;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.FileGraphCache;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.dependency.maven.MavenDependencySource;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.license.BundledLicenseCatalog;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compares the declared licenses Ward reads for a Maven project with the listing of the {@code
 * license-maven-plugin} that Ward replaced.
 *
 * <p>The plugin ran once; its names are committed in {@code expected.txt}, so the plugin need not
 * stay. Needs {@code mvn} and the fixture's artifacts in the local repository (the run is offline).
 * Run with {@code -Dward.license.parity.maven=true}.
 */
@EnabledIfSystemProperty(named = "ward.license.parity.maven", matches = "true")
class PomLicenseParitySmokeTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/license-parity/maven");
  private static final String COMMENT = "#";
  private static final String SEPARATOR = " = ";
  private static final String ALTERNATIVE = " | ";

  @TempDir Path work;

  @Test
  void declaredLicensesMatchTheRemovedPluginsListing() throws IOException {
    InventoryFixtures.copy(FIXTURE.toAbsolutePath(), work);
    final Path home = Path.of(System.getProperty("user.home"));
    final LicenseService service =
        new LicenseService(
            new DependencyInventoryService(
                Map.of(
                    BuildTool.MAVEN,
                    new MavenDependencySource(
                        new ProcessCommandExecutor(),
                        new FileGraphCache(),
                        new ForyJsonCodec(),
                        true))),
            Map.of(
                Ecosystem.MAVEN,
                new PomLicenseMetadata(
                    home.resolve(".m2/repository"),
                    home.resolve(".gradle/caches/modules-2/files-2.1"))),
            new BundledLicenseCatalog());

    final Set<String> actual =
        service
            .licenses(new Project(work, Language.JAVA, BuildTool.MAVEN, Framework.NONE), Set.of())
            .stream()
            .map(PomLicenseParitySmokeTest::line)
            .collect(Collectors.toCollection(TreeSet::new));

    assertThat(actual).isEqualTo(expected());
  }

  private static String line(final PackageLicense entry) {
    final String names =
        entry.lookup().present()
            ? entry.lookup().license().entries().stream()
                .map(DeclaredLicense.Entry::name)
                .collect(Collectors.joining(ALTERNATIVE))
            : "";
    return entry.dependency().id().name()
        + "@"
        + entry.dependency().id().version()
        + SEPARATOR
        + names;
  }

  private static Set<String> expected() throws IOException {
    return Files.readAllLines(FIXTURE.resolve("expected.txt")).stream()
        .filter(line -> !line.startsWith(COMMENT) && !line.isBlank())
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
