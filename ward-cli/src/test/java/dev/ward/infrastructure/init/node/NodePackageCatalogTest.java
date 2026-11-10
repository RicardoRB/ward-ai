package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.init.NodeInputs;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.domain.project.node.PackageManifest;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every Node gate init offers has its pinned packages, and packages that must match do. */
class NodePackageCatalogTest {
  @TempDir Path root;

  @Test
  void everyOfferedNodeGateHasItsPackagesInTheCatalog() {
    // Semgrep is a standalone binary, so no npm package backs it.
    final Set<String> standalone =
        new HashSet<>(gateIds(BuildTool.MAVEN, Language.JAVA, PackageManifest.EMPTY));
    standalone.add("semgrep");
    final PackageManifest jest = new PackageManifest(Map.of("jest", "^30.0.0"), null, null);
    for (final Language language : List.of(Language.TYPESCRIPT, Language.JAVASCRIPT)) {
      final Set<String> node =
          Stream.of(
                  gateIds(BuildTool.NPM, language, PackageManifest.EMPTY),
                  gateIds(BuildTool.NPM, language, jest),
                  gateIds(BuildTool.BUN, language, PackageManifest.EMPTY))
              .flatMap(Set::stream)
              .filter(id -> !standalone.contains(id))
              .collect(Collectors.toSet());

      assertThat(node).as("%s gates", language).isNotEmpty();
      assertThat(NodePackageCatalog.gates()).containsAll(node);
      node.forEach(
          gate ->
              assertThat(NodePackageCatalog.packages(gate))
                  .allSatisfy(
                      name ->
                          assertThat(NodePackageCatalog.find(name))
                              .as("package %s of %s gate %s", name, language, gate)
                              .isPresent()));
    }
  }

  @Test
  void vitestMatchesItsCoverageProvider() {
    assertThat(NodePackageCatalog.find("@vitest/coverage-v8").orElseThrow().version())
        .isEqualTo(NodePackageCatalog.find("vitest").orElseThrow().version());
  }

  @Test
  void declaredPackagesAreNotAddedAgain() {
    final PackageManifest manifest =
        new PackageManifest(
            Map.of("vitest", "^4.0.0", "@vitest/coverage-v8", "^4.0.0", "typescript", "~5.9.3"),
            null,
            null);

    assertThat(NodePackageCatalog.missing(List.of(gate("vitest"), gate("tsc")), manifest))
        .isEmpty();
    assertThat(NodePackageCatalog.missing(List.of(gate("stryker")), manifest))
        .extracting(NodePackageCatalog.Package::name)
        .containsExactly("@stryker-mutator/core");
  }

  private Set<String> gateIds(
      final BuildTool buildTool, final Language language, final PackageManifest manifest) {
    final InitSetupCatalog catalog =
        new InitSetupCatalog(
            new FakeEnvironmentInspection(),
            new NodeInputs(
                directory -> manifest, directory -> true, (directory, gate) -> Optional.empty()),
            null,
            null,
            "dev");
    return catalog
        .gates(
            new Project(
                root,
                language,
                buildTool,
                Framework.NONE,
                false,
                false,
                root,
                Set.of(),
                "24",
                TaskRunner.NONE,
                null),
            null)
        .stream()
        .map(InitGateOption::id)
        .collect(Collectors.toSet());
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of(id), true, "available");
  }
}
