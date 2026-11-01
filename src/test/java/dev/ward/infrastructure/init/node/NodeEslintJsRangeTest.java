package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.PackageManifest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An exact ESLint release gets the @eslint/js range of its major, which does exist on npm. */
class NodeEslintJsRangeTest {
  private static final String ESLINT = "eslint";

  @Test
  void exactEslintReleaseFollowsItsMajorForEslintJs() {
    final PackageManifest exact = new PackageManifest(Map.of(ESLINT, "10.9.1"), null, null);
    final InitGateOption eslint =
        new InitGateOption(ESLINT, "ESLint", List.of("npx", ESLINT), true, "ok");

    final List<NodePackageCatalog.Package> missing =
        NodePackageCatalog.missing(Language.TYPESCRIPT, List.of(eslint), exact);

    assertThat(missing)
        .filteredOn(tool -> "@eslint/js".equals(tool.name()))
        .singleElement()
        .satisfies(tool -> assertThat(tool.version()).isEqualTo("^10"));
  }
}
