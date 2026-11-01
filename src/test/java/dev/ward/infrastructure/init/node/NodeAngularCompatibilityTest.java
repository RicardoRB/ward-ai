package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.CompatibilityOutcome;
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

/** Angular major, TypeScript, angular-eslint and Node findings reported by init. */
class NodeAngularCompatibilityTest {
  @TempDir Path root;

  @Test
  void supportedAngularMajorsAcceptTheirDeclaredTypeScriptAndAngularEslintVersions() {
    final PackageManifest angular21 =
        new PackageManifest(
            Map.of(
                NodePackageCatalog.ANGULAR_CORE,
                "^21.2.0",
                NodePackageCatalog.TYPESCRIPT,
                "~5.9.3",
                NodePackageCatalog.ANGULAR_ESLINT,
                "21.4.0"),
            null,
            null);
    final PackageManifest angular22 =
        new PackageManifest(
            Map.of(
                NodePackageCatalog.ANGULAR_CORE,
                "^22.2.0",
                NodePackageCatalog.TYPESCRIPT,
                "6.0.3",
                NodePackageCatalog.ANGULAR_ESLINT,
                "22.6.0"),
            null,
            null);

    assertThat(check("22", angular21).findings()).isEmpty();
    assertThat(check("22", angular22).findings()).isEmpty();
  }

  @Test
  void unsupportedAngularMajorIsIncompatibleAndSkipsDerivativeFindings() {
    final PackageManifest manifest =
        new PackageManifest(
            Map.of(
                NodePackageCatalog.ANGULAR_CORE,
                "^19.0.0",
                NodePackageCatalog.TYPESCRIPT,
                "7.0.2",
                NodePackageCatalog.ANGULAR_ESLINT,
                "^22.0.0"),
            null,
            null);

    assertThat(check("20", manifest).findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.artifactId()).isEqualTo(NodePackageCatalog.ANGULAR_CORE);
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.message()).contains("supported Angular majors 21, 22");
            });
  }

  @Test
  void typescriptOutsideAngularRangeIsIncompatible() {
    final PackageManifest manifest =
        new PackageManifest(
            Map.of(
                NodePackageCatalog.ANGULAR_CORE, "^21.2.0", NodePackageCatalog.TYPESCRIPT, "6.1.0"),
            null,
            null);

    assertThat(check("24", manifest).findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.artifactId()).isEqualTo(NodePackageCatalog.TYPESCRIPT);
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.message()).contains(">=5.9 <6.1", "TypeScript 6.1.0");
            });
  }

  @Test
  void angularEslintMajorMismatchIsIncompatible() {
    final PackageManifest manifest =
        new PackageManifest(
            Map.of(
                NodePackageCatalog.ANGULAR_CORE,
                "^21.2.0",
                NodePackageCatalog.ANGULAR_ESLINT,
                "^22.0.0"),
            null,
            null);

    assertThat(check("24", manifest).findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.artifactId()).isEqualTo(NodePackageCatalog.ANGULAR_ESLINT);
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.message()).contains("does not match Angular 21", "21.4.0");
            });
  }

  @Test
  void angularNodeOutsideRangeIsReportedOnAngularCore() {
    final PackageManifest manifest =
        new PackageManifest(Map.of(NodePackageCatalog.ANGULAR_CORE, "^22.2.0"), null, null);

    assertThat(check("20", manifest).findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.artifactId()).isEqualTo(NodePackageCatalog.ANGULAR_CORE);
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.INCOMPATIBLE);
              assertThat(finding.message()).contains("^22.22.3 || ^24.15.0 || >=26.0.0", "Node 20");
            });
  }

  private NodeCompatibility check(
      final String node, final PackageManifest manifest, final String... packages) {
    return NodeCompatibility.check(
        project(node),
        manifest,
        List.of(packages).stream()
            .map(name -> NodePackageCatalog.find(name).orElseThrow())
            .toList(),
        Set.of());
  }

  private Project project(final String node) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        node,
        TaskRunner.NONE,
        null);
  }
}
