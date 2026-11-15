package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.domain.project.Framework;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code ward.toml} architecture tables (layers, cycles, independence) over Kotlin sources,
 * with the real frontends of the gate.
 */
class KotlinArchitectureTablesTest {
  private static final String KOTLIN = "src/main/kotlin/shop/";
  private static final String LEGACY_COUPON = KOTLIN + "legacy/Coupon.kt";
  private static final String DOMAIN_DISCOUNT = KOTLIN + "domain/Discount.kt";
  private static final String LAYERS = "ward:architecture/layers";
  private static final String HEADER = "version = 2\n[checks.ward]\nuses = \"ward\"\n";
  private static final String LEGACY_TO_DOMAIN =
      HEADER + "[[checks.ward.layers]]\nfrom = [\"legacy\"]\nforbidden = [\"domain\"]\n";
  private static final String DOMAIN_TO_LEGACY =
      HEADER + "[[checks.ward.layers]]\nfrom = [\"domain\"]\nforbidden = [\"legacy\"]\n";
  private static final String CYCLES =
      HEADER + "[checks.ward.cycles]\nroots = [\"src/main/kotlin/shop\"]\n";
  private static final String INDEPENDENCE =
      HEADER
          + "[[checks.ward.independence]]\n"
          + "modules = [\"src/main/kotlin/shop/legacy\", \"src/main/kotlin/shop/domain\"]\n";
  private static final int IMPORT_LINE = 3;

  @TempDir Path root;

  @Test
  void aKotlinFileOfAFromLayerThatImportsAForbiddenLayerIsReported() throws IOException {
    kotlinProject();

    final List<Finding> findings = run(LEGACY_TO_DOMAIN);

    assertThat(findings).extracting(Finding::ruleId).containsOnly(LAYERS);
    assertThat(findings).extracting(Finding::file).containsExactly(LEGACY_COUPON);
    assertThat(findings.getFirst().line()).isEqualTo(IMPORT_LINE);
  }

  @Test
  void aKotlinTestSourceIsNotChecked() throws IOException {
    kotlinProject();
    write(
        "src/test/kotlin/shop/legacy/CouponTest.kt",
        "package shop.legacy\n\nimport shop.domain.Discount\n\nclass CouponTest(val d: Discount)\n");

    assertThat(run(LEGACY_TO_DOMAIN)).extracting(Finding::file).containsExactly(LEGACY_COUPON);
  }

  @Test
  void theAllowedDirectionOfAKotlinProjectIsNotReported() throws IOException {
    kotlinProject();

    assertThat(run(DOMAIN_TO_LEGACY)).isEmpty();
  }

  @Test
  void theHexagonalBoundaryThatInitWritesIsEnforcedOnKotlin() throws IOException {
    final LayerBoundary hexagonal = ArchitectureLayers.forStyle("hexagonal", Framework.NONE);
    write(
        "src/main/kotlin/shop/domain/Order.kt",
        "package shop.domain\n\nimport shop.adapter.Db\n\nclass Order(val d: Db)\n");
    write("src/main/kotlin/shop/adapter/Db.kt", "package shop.adapter\n\nclass Db\n");

    final List<Finding> findings =
        run(
            HEADER
                + "[[checks.ward.layers]]\nfrom = "
                + quoted(hexagonal.from())
                + "\nforbidden = "
                + quoted(hexagonal.forbidden())
                + "\n");

    assertThat(findings)
        .extracting(Finding::file)
        .containsExactly("src/main/kotlin/shop/domain/Order.kt");
  }

  @Test
  void aKotlinSliceCycleIsReported() throws IOException {
    kotlinProject();
    write(
        DOMAIN_DISCOUNT,
        "package shop.domain\n\nimport shop.legacy.Coupon\n\nclass Discount(val c: Coupon?)\n");

    final List<Finding> findings = run(CYCLES);

    assertThat(findings).extracting(Finding::ruleId).containsOnly("ward:architecture/cycles");
    assertThat(findings).extracting(Finding::file).containsExactly(DOMAIN_DISCOUNT);
  }

  @Test
  void aKotlinImportBetweenIndependentModulesIsReported() throws IOException {
    kotlinProject();

    final List<Finding> findings = run(INDEPENDENCE);

    assertThat(findings).extracting(Finding::ruleId).containsOnly("ward:architecture/independence");
    assertThat(findings).extracting(Finding::file).containsExactly(LEGACY_COUPON);
  }

  private static String quoted(final List<String> names) {
    return names.stream().map(name -> "\"" + name + "\"").toList().toString();
  }

  private List<Finding> run(final String toml) throws IOException {
    final Path ward = root.resolve("ward.toml");
    Files.writeString(ward, toml);
    final GateConfiguration check = new TomlConfigurationReader().read(ward).checks().get("ward");
    return ArchitectureGateRun.run(root, check).findings().items();
  }

  private void kotlinProject() throws IOException {
    write(DOMAIN_DISCOUNT, "package shop.domain\n\nclass Discount(val percent: Int)\n");
    write(
        LEGACY_COUPON,
        "package shop.legacy\n\nimport shop.domain.Discount\n\nclass Coupon(val d: Discount)\n");
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
