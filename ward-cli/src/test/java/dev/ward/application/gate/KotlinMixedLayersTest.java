package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.Finding;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code ward:architecture/layers} over the mixed Java and Kotlin fixture, in both directions. */
class KotlinMixedLayersTest {
  private static final String HEADER = "version = 2\n[checks.ward]\nuses = \"ward\"\n";
  private static final String LEGACY_TO_DOMAIN =
      HEADER + "[[checks.ward.layers]]\nfrom = [\"legacy\"]\nforbidden = [\"domain\"]\n";
  private static final String DOMAIN_TO_LEGACY =
      HEADER + "[[checks.ward.layers]]\nfrom = [\"domain\"]\nforbidden = [\"legacy\"]\n";

  @TempDir Path root;

  @Test
  void aKotlinFileImportingAJavaFileOfAForbiddenLayerIsReported() throws IOException {
    ArchitectureGateRun.copy(
        Path.of("src/test/resources/fixtures/kotlin-resolution"), root, file -> true);

    final List<String> files = runIn(LEGACY_TO_DOMAIN).stream().map(Finding::file).toList();

    assertThat(files).contains("domain/src/main/java/shop/legacy/LegacyBridge.kt");
  }

  @Test
  void aKotlinFileOfADomainLayerThatImportsAJavaFileOfLegacyIsReported() throws IOException {
    ArchitectureGateRun.copy(
        Path.of("src/test/resources/fixtures/kotlin-resolution"), root, file -> true);
    write(
        "domain/src/main/kotlin/shop/domain/Back.kt",
        "package shop.domain\n\nimport shop.legacy.Report\n\nclass Back(val r: Report)\n");

    final List<String> files = runIn(DOMAIN_TO_LEGACY).stream().map(Finding::file).toList();

    assertThat(files).contains("domain/src/main/kotlin/shop/domain/Back.kt");
  }

  @Test
  void aJavaFileOfAFromLayerThatImportsAKotlinFileOfAForbiddenLayerIsReported() throws IOException {
    ArchitectureGateRun.copy(
        Path.of("src/test/resources/fixtures/kotlin-resolution"), root, file -> true);
    write(
        "domain/src/main/java/shop/legacy/Uses.java",
        "package shop.legacy;\n\nimport shop.domain.Discount;\n\npublic final class Uses {\n"
            + "  Discount d;\n}\n");

    final List<String> files = runIn(LEGACY_TO_DOMAIN).stream().map(Finding::file).toList();

    assertThat(files).contains("domain/src/main/java/shop/legacy/Uses.java");
  }

  private List<Finding> runIn(final String toml) throws IOException {
    final Path ward = root.resolve("ward.toml");
    Files.writeString(ward, toml);
    final GateConfiguration check = new TomlConfigurationReader().read(ward).checks().get("ward");
    return ArchitectureGateRun.run(root, check).findings().items();
  }

  private void write(final String path, final String text) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
