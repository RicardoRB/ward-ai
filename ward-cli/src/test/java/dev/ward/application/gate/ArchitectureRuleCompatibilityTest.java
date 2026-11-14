package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.Finding;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tables of {@code ward.toml} keep their findings, and each gives the files and lines of its
 * longhand in {@code ward-architecture.toml}.
 */
class ArchitectureRuleCompatibilityTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static final String WARD_CHECK = "version = 2\n[checks.ward]\nuses = \"ward\"\n";
  private static final String LAYERS = "ward:architecture/layers";
  private static final String INDEPENDENCE = "ward:architecture/independence";
  private static final String DEPENDENCIES = "ward:architecture/dependencies";

  @TempDir Path root;

  @Test
  void aLayersBoundaryReportsTheFilesAndLinesOfItsLonghand() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("java-resolution"), root, file -> true);
    final Path testSource = root.resolve("src/test/java/shop/legacy/CouponTest.java");
    Files.createDirectories(testSource.getParent());
    Files.writeString(
        testSource,
        "package shop.legacy;\n\nimport shop.domain.Discount;\n\nclass CouponTest {}\n");

    final Set<String> shorthand =
        positions(LAYERS, wardToml("layers = [{ from = [\"legacy\"], forbidden = [\"domain\"] }]"));
    Files.delete(root.resolve("ward.toml"));
    final Set<String> longhand =
        positions(
            DEPENDENCIES,
            architectureToml(
                """
                [[rule]]
                name = "legacy-layer"
                select = { directories = ["legacy"], exclude-paths = ["src/test/**", "**/src/test/**"] }
                must-not-depend-on = { directories = ["domain"] }
                because = "legacy stays isolated"
                """));

    assertThat(shorthand).containsExactly("src/main/java/shop/legacy/Coupon.java:3");
    assertThat(longhand).isEqualTo(shorthand);
  }

  @Test
  void anIndependenceTableReportsTheFilesAndLinesOfItsLonghand() throws IOException {
    final Path base = FIXTURES.resolve("node-slice-cycles/typescript");
    ArchitectureGateRun.copy(base, root, file -> true);

    final Set<String> shorthand =
        positions(
            INDEPENDENCE,
            wardToml("independence = [{ modules = [\"src/orders\", \"src/billing\"] }]"));
    Files.delete(root.resolve("ward.toml"));
    final Set<String> longhand =
        positions(
            DEPENDENCIES,
            architectureToml(
                """
                [[rule]]
                name = "orders-independent"
                select = { paths = ["src/orders/**"] }
                must-not-depend-on = { paths = ["src/billing/**"] }
                because = "slices are independent"

                [[rule]]
                name = "billing-independent"
                select = { paths = ["src/billing/**"] }
                must-not-depend-on = { paths = ["src/orders/**"] }
                because = "slices are independent"
                """));

    assertThat(shorthand).hasSize(2);
    assertThat(longhand).isEqualTo(shorthand);
  }

  @Test
  void aTableInWardTomlAndARuleInTheFileBothRun() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("java-resolution"), root, file -> true);
    final GateConfiguration check =
        wardToml("layers = [{ from = [\"legacy\"], forbidden = [\"domain\"] }]");
    architectureToml(
        """
        [[rule]]
        name = "legacy-layer"
        select = { directories = ["legacy"] }
        must-not-depend-on = { directories = ["domain"] }
        because = "legacy stays isolated"
        """);

    final List<Finding> findings = ArchitectureGateRun.run(root, check).findings().items();

    assertThat(findings)
        .extracting(Finding::ruleId)
        .containsExactlyInAnyOrder(LAYERS, DEPENDENCIES);
    assertThat(findings.stream().filter(finding -> finding.ruleId().equals(LAYERS)).toList())
        .singleElement()
        .satisfies(
            finding ->
                assertThat(finding.message())
                    .startsWith("Layer legacy must not depend on domain: "));
  }

  @Test
  void aProjectWithoutTheFileReportsTheLayersFindingsAlone() throws IOException {
    ArchitectureGateRun.copy(FIXTURES.resolve("java-resolution"), root, file -> true);

    final List<Finding> findings =
        ArchitectureGateRun.run(
                root, wardToml("layers = [{ from = [\"legacy\"], forbidden = [\"domain\"] }]"))
            .findings()
            .items();

    assertThat(findings).extracting(Finding::ruleId).containsExactly(LAYERS);
  }

  private Set<String> positions(final String ruleId, final GateConfiguration check) {
    return ArchitectureGateRun.run(root, check).findings().items().stream()
        .filter(finding -> ruleId.equals(finding.ruleId()))
        .map(finding -> finding.file() + ":" + finding.line())
        .collect(Collectors.toSet());
  }

  private GateConfiguration wardToml(final String extra) throws IOException {
    final Path file = root.resolve("ward.toml");
    Files.writeString(file, WARD_CHECK + extra + "\n");
    return new TomlConfigurationReader().read(file).checks().get("ward");
  }

  private GateConfiguration architectureToml(final String rules) throws IOException {
    Files.writeString(root.resolve("ward-architecture.toml"), "version = 1\n\n" + rules);
    return new GateConfiguration("ward", true, List.of());
  }
}
