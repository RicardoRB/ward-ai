package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.init.NodeInputs;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
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
  private static final String ESLINT = "eslint";
  @TempDir Path root;

  @Test
  void everyOfferedNodeGateHasItsPackagesInTheCatalog() {
    final Set<String> standalone = gateIds(BuildTool.MAVEN, Language.JAVA, PackageManifest.EMPTY);
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
              assertThat(NodePackageCatalog.packages(language, gate))
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
  void typescriptIsInsideTypescriptEslintsRange() {
    assertThat(
            NodeVersionRange.matches(
                NodePackageCatalog.find("typescript-eslint").orElseThrow().typescript(),
                NodePackageCatalog.find("typescript").orElseThrow().version()))
        .isTrue();
  }

  @Test
  void pinnedEslintIsInsideTheSupportedEslintRange() {
    assertThat(
            NodeVersionRange.matches(
                NodePackageCatalog.ESLINT_SUPPORTED,
                NodePackageCatalog.find(ESLINT).orElseThrow().version()))
        .isTrue();
  }

  @Test
  void declaredPackagesAreNotAddedAgain() {
    final PackageManifest manifest =
        new PackageManifest(Map.of("prettier", "^3.3.0", "typescript", "~5.9.3"), null, null);

    assertThat(
            NodePackageCatalog.missing(
                Language.TYPESCRIPT, List.of(gate("prettier"), gate("tsc")), manifest))
        .isEmpty();
    assertThat(NodePackageCatalog.missing(Language.TYPESCRIPT, List.of(gate(ESLINT)), manifest))
        .extracting(NodePackageCatalog.Package::name)
        .containsExactly(ESLINT, "@eslint/js", "typescript-eslint");
  }

  @Test
  void javaScriptEslintAddsGlobalsAndNoTypeScriptPackages() {
    assertThat(
            NodePackageCatalog.missing(
                Language.JAVASCRIPT,
                List.of(gate(ESLINT), gate("prettier")),
                PackageManifest.EMPTY))
        .extracting(NodePackageCatalog.Package::name)
        .containsExactly(ESLINT, "@eslint/js", "globals", "prettier");
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
                root, language, buildTool, Framework.NONE, false, false, root, Set.of(), "24"),
            null)
        .stream()
        .map(InitGateOption::id)
        .collect(Collectors.toSet());
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of(id), true, "available");
  }

  @Test
  void eslintJsFollowsADeclaredEslintRange() {
    final PackageManifest nextStyle = new PackageManifest(Map.of(ESLINT, "^9"), null, null);
    final InitGateOption eslint =
        new InitGateOption(ESLINT, "ESLint", List.of("npx", ESLINT), true, "ok");

    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(Language.TYPESCRIPT, List.of(eslint), nextStyle);

    assertThat(missing)
        .filteredOn(tool -> "@eslint/js".equals(tool.name()))
        .singleElement()
        .satisfies(tool -> assertThat(tool.version()).isEqualTo("^9"));
  }

  @Test
  void eslintJsKeepsItsPinWhenESLintIsNotDeclared() {
    final InitGateOption eslint =
        new InitGateOption(ESLINT, "ESLint", List.of("npx", ESLINT), true, "ok");

    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(Language.TYPESCRIPT, List.of(eslint), PackageManifest.EMPTY);

    assertThat(missing)
        .filteredOn(tool -> "@eslint/js".equals(tool.name()))
        .singleElement()
        .satisfies(
            tool ->
                assertThat(tool.version())
                    .isEqualTo(NodePackageCatalog.find("@eslint/js").orElseThrow().version()));
  }
}
