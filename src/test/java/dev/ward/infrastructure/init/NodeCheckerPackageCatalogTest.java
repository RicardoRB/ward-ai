package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The framework checker packages: pins inside their ranges, and added with their gate. */
class NodeCheckerPackageCatalogTest {
  @Test
  void frameworkCheckerPinsAreInsideTheirSupportedRanges() {
    final List<String> outside =
        NodePackageCatalog.CHECKER_SUPPORTED.keySet().stream()
            .filter(
                name ->
                    !NodeVersionRange.matches(
                        NodePackageCatalog.CHECKER_SUPPORTED.get(name),
                        NodePackageCatalog.find(name).orElseThrow().version()))
            .toList();

    assertThat(outside).isEmpty();
  }

  @Test
  void frameworkGatesAddTheirCheckerAndTypeScriptWhenMissing() {
    assertThat(
            NodePackageCatalog.missing(
                    Language.TYPESCRIPT,
                    List.of(gate("vue-tsc")),
                    new PackageManifest(Map.of("vue", "3.5.0"), null, null))
                .stream()
                .map(NodePackageCatalog.Package::name))
        .containsExactly("vue-tsc", "typescript");
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of(id), true, "available");
  }
}
