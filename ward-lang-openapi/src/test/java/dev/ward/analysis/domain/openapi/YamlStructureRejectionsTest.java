package dev.ward.analysis.domain.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YamlStructureRejectionsTest {

  @Test
  void rejectsADuplicateKeyNamingBothLines() {
    assertThat(YamlTrees.dump("get: 1\nput: 2\nget: 3\n"))
        .isEqualTo("ERROR 3:1 duplicate key 'get' (lines 1 and 3)");
  }

  @Test
  void rejectsADuplicateKeyInAFlowMapping() {
    assertThat(YamlTrees.dump("{\"a\": 1, \"a\": 2}")).startsWith("ERROR 1:10 duplicate key 'a'");
  }

  @Test
  void rejectsMoreThanOneDocument() {
    assertThat(YamlTrees.dump("a: 1\n---\nb: 2\n"))
        .isEqualTo("ERROR 2:1 more than one document in a file is not supported");
    assertThat(YamlTrees.dump("---\na: 1\n...\n")).isEqualTo("{a=1}");
  }

  @Test
  void rejectsBadIndentationAndNestedMappingsOnTheKeyLine() {
    assertThat(YamlTrees.dump("a: 1\n  b: 2\n")).startsWith("ERROR 2:3 ");
    assertThat(YamlTrees.dump("a: b: c\n")).isEqualTo("ERROR 1:4 a mapping is not allowed here");
    assertThat(YamlTrees.dump("a: 1\n- b\n")).startsWith("ERROR 2:1 ");
  }

  @Test
  void aSyntaxErrorOnALaterLineNamesThatLine() {
    assertThat(YamlTrees.dump("a:\n  b: 1\n  c: 2\n d: 3\n")).startsWith("ERROR 4:2 ");
  }

  @Test
  void anUnreadableFlowCollectionIsADiagnostic() {
    assertThat(YamlTrees.dump("[1, 2")).startsWith("ERROR ");
    assertThat(YamlTrees.dump("{\"a\": 1,, }")).startsWith("ERROR ");
  }
}
