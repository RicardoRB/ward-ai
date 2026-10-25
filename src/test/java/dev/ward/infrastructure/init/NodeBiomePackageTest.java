package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The package a Biome gate adds: Biome alone, pinned, with no ESLint or Prettier package. */
class NodeBiomePackageTest {
  private static final InitGateOption BIOME =
      new InitGateOption("biome", "Biome", List.of("npx", "biome"), true, "ok");

  @Test
  void biomeGateAddsOnlyTheBiomePackageWhenMissing() {
    assertThat(
            NodePackageCatalog.missing(Language.TYPESCRIPT, List.of(BIOME), PackageManifest.EMPTY))
        .singleElement()
        .satisfies(
            tool -> {
              assertThat(tool.name()).isEqualTo("@biomejs/biome");
              assertThat(tool.version()).isEqualTo("2.5.15");
            });
  }

  @Test
  void declaredBiomeIsNotAddedAgain() {
    final PackageManifest declared =
        new PackageManifest(Map.of("@biomejs/biome", "^2.6.0"), null, null);

    assertThat(NodePackageCatalog.missing(Language.JAVASCRIPT, List.of(BIOME), declared)).isEmpty();
  }
}
