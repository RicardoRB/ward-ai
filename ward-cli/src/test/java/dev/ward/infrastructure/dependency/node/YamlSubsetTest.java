package dev.ward.infrastructure.dependency.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.LockfileFormatException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YamlSubsetTest {
  @Test
  void readsBlockMapsQuotedKeysFlowCollectionsAndSequences() {
    final Map<String, Object> root =
        YamlSubset.parse(
            """
            # comment
            lockfileVersion: '9.0'
            packages:
              '@babel/core@7.0.0':
                resolution: {integrity: sha512-abc==}
                engines: {node: '>=6.9.0'}
                cpu: [x64, arm64]
              "a@1":
                dev: false
            onlyBuilt:
              - esbuild
              - sharp
            empty: {}
            """);

    assertThat(root.get("lockfileVersion")).isEqualTo("9.0");
    final Map<String, Object> core =
        YamlSubset.map(YamlSubset.map(root.get("packages")).get("@babel/core@7.0.0"));
    assertThat(YamlSubset.map(core.get("resolution"))).containsEntry("integrity", "sha512-abc==");
    assertThat(YamlSubset.map(core.get("engines"))).containsEntry("node", ">=6.9.0");
    assertThat(core.get("cpu")).isEqualTo(List.of("x64", "arm64"));
    assertThat(root.get("onlyBuilt")).isEqualTo(List.of("esbuild", "sharp"));
    assertThat(YamlSubset.map(root.get("empty"))).isEmpty();
  }

  @Test
  void aShapeItDoesNotKnowFailsWithItsLineNumber() {
    assertThatThrownBy(() -> YamlSubset.parse("a:\n  b: 1\n   c: 2\n"))
        .isInstanceOf(LockfileFormatException.class)
        .hasMessageContaining("line 3");
    assertThatThrownBy(() -> YamlSubset.parse("a: |\n  text\n"))
        .isInstanceOf(LockfileFormatException.class)
        .hasMessageContaining("line 1");
    assertThatThrownBy(() -> YamlSubset.parse("a: {b: [1\n"))
        .isInstanceOf(LockfileFormatException.class)
        .hasMessageContaining("line 1");
  }
}
