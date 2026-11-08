package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The package.json reader: key positions and the keys dead-code rules read. */
final class NodeManifestTest {

  private static final String PACKAGE =
      """
      {
        "name": "app",
        "main": "./dist/index.js",
        "workspaces": ["packages/*", "apps/web"],
        "dependencies": {
          "react": "^18.2.0"
        },
        "devDependencies": { "vitest": "^3" },
        "scripts": { "build": "tsc -p ." },
        "bin": "./cli.js",
        "flag": true, "missing": null, "count": -1.5e3
      }
      """;

  @Test
  void readsTheStringKeysOfTheManifest() {
    final NodeManifest manifest = NodeManifest.parse(PACKAGE).orElseThrow();

    assertThat(manifest.text("name")).contains("app");
    assertThat(manifest.text("main")).contains("./dist/index.js");
    assertThat(manifest.text("module")).isEmpty();
    assertThat(manifest.text("workspaces")).isEmpty();
  }

  @Test
  void recordsTheLineAndColumnOfEachKey() {
    final NodeManifest manifest = NodeManifest.parse(PACKAGE).orElseThrow();

    assertThat(manifest.member("main")).isPresent();
    assertThat(manifest.member("main").orElseThrow().line()).isEqualTo(3);
    assertThat(manifest.member("main").orElseThrow().column()).isEqualTo(3);
  }

  @Test
  void readsDependencyEntriesWithTheirKeyPositions() {
    final NodeManifest manifest = NodeManifest.parse(PACKAGE).orElseThrow();

    assertThat(manifest.entries("dependencies"))
        .extracting(NodeManifest.Member::key)
        .containsExactly("react");
    assertThat(manifest.entries("dependencies").get(0).line()).isEqualTo(6);
    assertThat(manifest.entries("devDependencies"))
        .extracting(NodeManifest.Member::key)
        .containsExactly("vitest");
    assertThat(manifest.entries("peerDependencies")).isEmpty();
  }

  @Test
  void readsArrayStringsAndScriptEntries() {
    final NodeManifest manifest = NodeManifest.parse(PACKAGE).orElseThrow();

    assertThat(manifest.strings("workspaces")).containsExactly("packages/*", "apps/web");
    assertThat(manifest.entries("scripts"))
        .extracting(NodeManifest.Member::key)
        .containsExactly("build");
  }

  @Test
  void treatsAMissingOrWrongTypedKeyAsAbsent() {
    final NodeManifest manifest =
        NodeManifest.parse("{\"main\": 3, \"name\": \"x\"}").orElseThrow();

    assertThat(manifest.text("main")).isEmpty();
    assertThat(manifest.strings("main")).isEmpty();
    assertThat(manifest.entries("name")).isEmpty();
    assertThat(manifest.strings("name")).isEmpty();
  }

  @Test
  void decodesEscapesInStrings() {
    final NodeManifest manifest =
        NodeManifest.parse("{\"name\": \"a\\\"b\\u0041\\n\"}").orElseThrow();

    assertThat(manifest.text("name")).contains("a\"bA\n");
  }

  @Test
  void rejectsMalformedInputAsEmpty() {
    assertThat(NodeManifest.parse("")).isEmpty();
    assertThat(NodeManifest.parse("[1, 2]")).isEmpty();
    assertThat(NodeManifest.parse("{\"name\": }")).isEmpty();
    assertThat(NodeManifest.parse("{\"name\": \"open")).isEmpty();
    assertThat(NodeManifest.parse("{\"a\": tru}")).isEmpty();
    assertThat(NodeManifest.parse("{} extra")).isEmpty();
    assertThat(NodeManifest.parse("{\"a\": \"\\q\"}")).isEmpty();
    assertThat(NodeManifest.parse("{\"a\": \"\\u12\"}")).isEmpty();
    assertThat(NodeManifest.parse("{\"a\": \"\\")).isEmpty();
    assertThat(NodeManifest.parse("{\"a\": ?}")).isEmpty();
    assertThat(NodeManifest.parse("{\"a\" 1}")).isEmpty();
    assertThat(NodeManifest.parse("{\"a\": [1 2]}")).isEmpty();
  }

  @Test
  void acceptsEmptyObjectsAndArrays() {
    final NodeManifest manifest = NodeManifest.parse("{\"a\": [], \"b\": {}}").orElseThrow();

    assertThat(manifest.strings("a")).isEmpty();
    assertThat(manifest.entries("b")).isEmpty();
    assertThat(manifest.member("a")).isPresent();
  }
}
