package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rewrites a matrix fixture's {@code package.json} for the packages a row declares. */
class NodeMatrixFixtureEditsTest {
  private static final String PACKAGE_JSON =
      """
      {
        "name": "astro-app",
        "scripts": {
          "astro": "astro"
        },
        "dependencies": {
          "astro": "^7.4.2"
        }
      }
      """;

  @Test
  void parsesScopedAndUnscopedPackages() {
    assertThat(NodeMatrixFixtureEdits.parsePackages("astro@7.3.8 @astrojs/check@0.9.12"))
        .containsExactly(Map.entry("astro", "7.3.8"), Map.entry("@astrojs/check", "0.9.12"));
    assertThat(NodeMatrixFixtureEdits.parsePackages("")).isEmpty();
  }

  @Test
  void replacesDeclaredVersionAndKeepsTheScriptOfTheSameName() {
    final String result =
        NodeMatrixFixtureEdits.withPackages(PACKAGE_JSON, Map.of("astro", "7.3.8"));

    assertThat(result)
        .contains("\"dependencies\": {\n    \"astro\": \"7.3.8\"\n  }")
        .contains("\"astro\": \"astro\"")
        .doesNotContain("^7.4.2");
  }

  @Test
  void addsUndeclaredPackageToDevDependencies() {
    final String result =
        NodeMatrixFixtureEdits.withPackages(PACKAGE_JSON, Map.of("typescript", "5.0.4"));

    assertThat(result)
        .contains("\"devDependencies\": {")
        .contains("\"typescript\": \"5.0.4\"")
        .contains("\"astro\": \"^7.4.2\"");
  }
}
