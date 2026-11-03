package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.node.PackageManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A project with a {@code ward} gate ignores the analysis result cache in its Git ignore file. */
class AnalysisCacheIgnoreTest {
  private static final Path ROOT = Path.of("/shop");
  private static final Project JAVA =
      new Project(ROOT, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
  private static final String CACHE = ".ward/cache/";

  @Test
  void wardGateIgnoresTheCacheDirectory() {
    assertThat(bodies(List.of(gate("ward")))).anyMatch(body -> body.contains(CACHE));
  }

  @Test
  void projectWithoutWardGateDoesNotIgnoreTheCacheDirectory() {
    assertThat(bodies(List.of(gate("checkstyle")))).noneMatch(body -> body.contains(CACHE));
  }

  @Test
  void cacheEntryIsWrittenOnceInTheIgnoreFile() {
    final List<String> bodies = bodies(List.of(gate("ward")));

    assertThat(bodies.stream().filter(body -> body.contains(CACHE)).count()).isEqualTo(1);
  }

  private static List<String> bodies(final List<InitGateOption> gates) {
    final InitSetupCatalog catalog =
        new InitSetupCatalog(
            new FakeEnvironmentInspection(),
            new NodeInputs(
                directory -> PackageManifest.EMPTY,
                directory -> false,
                (directory, gate) -> Optional.empty()),
            null,
            null,
            "dev");
    final InitSelection selection =
        new InitSelection(
            List.of("none"),
            List.of(),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            false,
            null,
            false,
            null);
    return OwnedSections.of(catalog, JAVA, selection, gates, List.of()).stream()
        .filter(section -> section.file().endsWith(".gitignore"))
        .map(OwnedSection::body)
        .toList();
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, List.of(id), true, "ok");
  }
}
