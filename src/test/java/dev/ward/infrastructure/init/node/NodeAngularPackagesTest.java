package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The packages and Node range an Angular project's gates need, from its Angular major. */
class NodeAngularPackagesTest {
  private static final int BELOW_RANGE_NODE = 20;
  private static final int IN_RANGE_NODE = 24;

  @TempDir Path root;

  @Test
  void angular22GetsPinnedAngularEslintAndCoverageAtDeclaredVitest() {
    final PackageManifest manifest =
        manifest(Map.of("@angular/core", "^22.2.0", "vitest", "^5.0.5"));

    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(Language.TYPESCRIPT, gates("ng-test", "eslint"), manifest);

    assertThat(byName(missing, "angular-eslint").version()).isEqualTo("22.6.0");
    assertThat(byName(missing, "@vitest/coverage-v8").version()).isEqualTo("^5.0.5");
  }

  @Test
  void angular21GetsItsOwnAngularEslintRelease() {
    final PackageManifest manifest =
        manifest(Map.of("@angular/core", "^21.2.0", "vitest", "^4.1.12"));

    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(Language.TYPESCRIPT, gates("ng-test", "eslint"), manifest);

    assertThat(byName(missing, "angular-eslint").version()).isEqualTo("21.4.0");
  }

  @Test
  void declaredAngularEslintIsNotAddedAgain() {
    final PackageManifest manifest =
        manifest(Map.of("@angular/core", "^22.2.0", "angular-eslint", "22.6.0"));

    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(Language.TYPESCRIPT, gates("ng-test", "eslint"), manifest);

    assertThat(missing)
        .extracting(NodePackageCatalog.Package::name)
        .doesNotContain("angular-eslint");
  }

  @Test
  void unsupportedAngularMajorDoesNotAddAngularEslint() {
    final PackageManifest manifest = manifest(Map.of("@angular/core", "^19.0.0"));

    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(Language.TYPESCRIPT, gates("ng-test", "eslint"), manifest);

    assertThat(missing)
        .extracting(NodePackageCatalog.Package::name)
        .doesNotContain("angular-eslint");
  }

  @Test
  void declaredAngularEslintOutsideNodeRangeIsIncompatible() {
    final PackageManifest manifest =
        manifest(Map.of("@angular/core", "^22.2.0", "angular-eslint", "22.6.0"));

    assertThat(findingsOn(BELOW_RANGE_NODE, manifest))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.artifactId()).isEqualTo("@angular/core");
              assertThat(finding.message()).contains("Angular 22 supports Node");
            });
  }

  @Test
  void declaredAngularEslintInsideNodeRangeIsCompatible() {
    final PackageManifest manifest =
        manifest(Map.of("@angular/core", "^22.2.0", "angular-eslint", "22.6.0"));

    assertThat(findingsOn(IN_RANGE_NODE, manifest)).isEmpty();
  }

  private List<CompatibilityFinding> findingsOn(final int node, final PackageManifest manifest) {
    final Project project =
        new Project(
            root,
            Language.TYPESCRIPT,
            BuildTool.NPM,
            Framework.ANGULAR,
            false,
            false,
            root,
            Set.of(),
            String.valueOf(node),
            TaskRunner.NONE,
            null);
    return NodeCompatibility.check(project, manifest, List.of(), Set.of()).findings();
  }

  private static NodePackageCatalog.Package byName(
      final List<NodePackageCatalog.Package> packages, final String name) {
    return packages.stream().filter(tool -> name.equals(tool.name())).findFirst().orElseThrow();
  }

  private static PackageManifest manifest(final Map<String, String> dependencies) {
    return new PackageManifest(dependencies, null, null);
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("npx", id), true, "available"))
        .toList();
  }
}
