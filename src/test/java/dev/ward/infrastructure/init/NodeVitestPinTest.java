package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Vitest and coverage versions init adds, chosen by the project's Vite and declared Vitest. */
class NodeVitestPinTest {
  private static final String VITEST = NodePackageCatalog.VITEST;

  @Test
  void vitestWithoutViteKeepsTheDefaultPin() {
    assertThat(pins(PackageManifest.EMPTY)).containsExactly("4.0.18", "4.0.18");
  }

  @Test
  void vitestFollowsTheDeclaredVite() {
    assertThat(pins(new PackageManifest(Map.of("vite", "^8.2.2"), null, null)))
        .containsExactly("4.1.11", "4.1.11");
    assertThat(pins(new PackageManifest(Map.of("vite", "7.3.7"), null, null)))
        .containsExactly("4.1.11", "4.1.11");
  }

  @Test
  void coverageFollowsADeclaredVitestInEveryProject() {
    final PackageManifest manifest =
        new PackageManifest(Map.of(VITEST, "4.0.18", "vite", "7.3.7"), null, null);

    assertThat(NodePackageCatalog.missing(Language.TYPESCRIPT, List.of(gate()), manifest))
        .singleElement()
        .satisfies(
            tool -> {
              assertThat(tool.name()).isEqualTo("@vitest/coverage-v8");
              assertThat(tool.version()).isEqualTo("4.0.18");
            });
  }

  private static List<String> pins(final PackageManifest manifest) {
    return NodePackageCatalog.missing(Language.TYPESCRIPT, List.of(gate()), manifest).stream()
        .map(NodePackageCatalog.Package::version)
        .toList();
  }

  private static InitGateOption gate() {
    return new InitGateOption(VITEST, VITEST, List.of(VITEST), true, "available");
  }
}
