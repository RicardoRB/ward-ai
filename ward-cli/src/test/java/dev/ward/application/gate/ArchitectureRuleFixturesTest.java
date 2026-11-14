package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The dependency rules of {@code ward-architecture.toml} through the {@code ward} gate, over the
 * resolution fixtures of every language that has dependency edges.
 */
class ArchitectureRuleFixturesTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static final String DEPENDENCIES = "ward:architecture/dependencies";
  private static final String VIOLATING =
      """
      version = 1

      [[rule]]
      name = "%s-must-not-reach-%s"
      select = { directories = ["%s"] }
      must-not-depend-on = { directories = ["%s"] }
      because = "the %s layer must stay free of %s"
      """;

  @TempDir Path root;

  @Test
  void javaFileReportsTheForbiddenImportAtItsLine() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("java-resolution"), root, file -> true);
    write("legacy", "domain");

    final GateResult result = ArchitectureGateRun.run(root);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    final Finding finding = onlyFinding(result);
    assertThat(finding.file()).isEqualTo("src/main/java/shop/legacy/Coupon.java");
    assertThat(finding.line()).isEqualTo(3);
    assertThat(finding.message())
        .startsWith("legacy-must-not-reach-domain: ")
        .contains("shop/domain/Discount.java")
        .endsWith("because the legacy layer must stay free of domain");
  }

  @Test
  void javaPassesWhenTheDirectionIsAllowed() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("java-resolution"), root, file -> true);
    write("domain", "legacy");

    assertThat(ArchitectureGateRun.run(root).status()).isEqualTo(GateStatus.PASSED);
  }

  @Test
  void kotlinFileInAMixedProjectReportsItsImportToAJavaFile() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("kotlin-resolution"), root, file -> true);
    write("legacy", "domain");

    final List<Finding> findings = ArchitectureGateRun.run(root).findings().items();

    assertThat(findings)
        .extracting(Finding::file)
        .contains("domain/src/main/java/shop/legacy/LegacyBridge.kt")
        .contains("domain/src/main/java/shop/legacy/Report.java");
    assertThat(findings).extracting(Finding::ruleId).containsOnly(DEPENDENCIES);
    assertThat(
            findings.stream()
                .filter(finding -> finding.file().endsWith("LegacyBridge.kt"))
                .findFirst()
                .orElseThrow()
                .line())
        .isEqualTo(3);
  }

  @ParameterizedTest
  @CsvSource({"typescript, ts", "javascript, js"})
  void nodeFileReportsTheForbiddenImport(final String dialect, final String extension)
      throws IOException {
    final Path base = FIXTURES.resolve("node-architecture").resolve(dialect);
    ArchitectureGateRun.copy(base, root, file -> !file.startsWith(base.resolve("violations")));
    ArchitectureGateRun.copy(base.resolve("violations/domain-infrastructure"), root, file -> true);
    write("domain", "infrastructure");

    final Finding finding = onlyFinding(ArchitectureGateRun.run(root));

    assertThat(finding.file()).isEqualTo("src/domain/leak." + extension);
    assertThat(finding.line()).isEqualTo(1);
  }

  @Test
  void pythonFileReportsItsImportsOfTheForbiddenPackage() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("python-resolution"), root, file -> true);
    write("controller", "repository");

    final List<Finding> findings = ArchitectureGateRun.run(root).findings().items();

    assertThat(findings).isNotEmpty();
    assertThat(findings)
        .allSatisfy(
            finding -> {
              assertThat(finding.file()).isEqualTo("src/shop/controller/clean.py");
              assertThat(finding.ruleId()).isEqualTo(DEPENDENCIES);
            });
  }

  @Test
  void swiftPackageFileReportsItsImportOfTheForbiddenModule() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("swift-resolution"), root, file -> true);
    write("Shop", "Domain");

    final Finding finding = onlyFinding(ArchitectureGateRun.run(root));

    assertThat(finding.file()).isEqualTo("Modules/Shop/Cart.swift");
    assertThat(finding.line()).isEqualTo(2);
  }

  @Test
  void swiftFolderWithoutAPackageFailsTheGateInsteadOfPassing() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("swift-architecture/layered"), root, file -> true);
    write("Controller", "Service");

    final GateResult result = ArchitectureGateRun.run(root);

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stdout() + result.stderr() + result.summary())
        .contains(
            "controller-must-not-reach-service", "SWIFT", "App/Controller/OrderController.swift");
  }

  private void write(final String from, final String forbidden) throws IOException {
    Files.writeString(
        root.resolve("ward-architecture.toml"),
        VIOLATING.formatted(
            from.toLowerCase(java.util.Locale.ROOT),
            forbidden.toLowerCase(java.util.Locale.ROOT),
            from,
            forbidden,
            from.toLowerCase(java.util.Locale.ROOT),
            forbidden.toLowerCase(java.util.Locale.ROOT)));
  }

  private static Finding onlyFinding(final GateResult result) {
    assertThat(result.findings().items()).hasSize(1);
    final Finding finding = result.findings().items().getFirst();
    assertThat(finding.ruleId()).isEqualTo(DEPENDENCIES);
    return finding;
  }
}
