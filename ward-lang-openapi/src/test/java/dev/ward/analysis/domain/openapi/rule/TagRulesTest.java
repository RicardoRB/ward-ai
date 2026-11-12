package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TagRulesTest {

  private static final String HEAD = "openapi: 3.0.3\ninfo:\n  title: T\npaths:\n";
  private static final String GET = "  /a:\n    get:\n      operationId: x\n%s";

  @Test
  void openapiTagsWantsAnArrayOfDistinctObjects() {
    assertThat(RuleFixtures.run("openapi-tags", "openapi: 3.0.3\n").getFirst().message())
        .contains("is missing and is required");
    assertThat(RuleFixtures.run("openapi-tags", "openapi: 3.0.3\ntags: x\n").getFirst().message())
        .contains("got string, want array");
    assertThat(RuleFixtures.run("openapi-tags", "openapi: 3.0.3\ntags: 1\n").getFirst().message())
        .contains("got integer");
    assertThat(
            RuleFixtures.run("openapi-tags", "openapi: 3.0.3\ntags:\n  - name: a\n  - name: a\n"))
        .extracting(OpenApiFinding::message)
        .singleElement()
        .asString()
        .contains("items at 0 and 1 are equal");
    assertThat(
            RuleFixtures.run("openapi-tags", "openapi: 3.0.3\ntags:\n  - name: a\n  - name: b\n"))
        .isEmpty();
    assertThat(RuleFixtures.run("openapi-tags", "openapi: 3.0.3\ntags: []\n")).isEmpty();
  }

  @Test
  void alphabeticalComparesTheNextTagWithANameAndSkipsTheOthers() {
    final String tags =
        "openapi: 3.0.3\ntags:\n  - name: c\n  - description: no name\n  - name: a\n";
    assertThat(RuleFixtures.run("openapi-tags-alphabetical", tags))
        .extracting(OpenApiFinding::message)
        .contains(
            "Tags must be in alphabetical order: `a` must be placed before `c` (alphabetical)");
    assertThat(
            RuleFixtures.run(
                "openapi-tags-alphabetical", "openapi: 3.0.3\ntags:\n  - name: a\n  - name: b\n"))
        .isEmpty();
    assertThat(RuleFixtures.run("openapi-tags-alphabetical", "openapi: 3.0.3\n")).isEmpty();
  }

  @Test
  void tagDescriptionNamesTheTag() {
    assertThat(RuleFixtures.run("tag-description", "openapi: 3.0.3\ntags:\n  - name: a\n"))
        .extracting(OpenApiFinding::message)
        .containsExactly("tag `a` must have a description");
  }

  @Test
  void operationTagRulesCheckPresenceDefinitionAndCount() {
    assertThat(RuleFixtures.run("operation-tags", HEAD + String.format(GET, "")))
        .extracting(OpenApiFinding::message)
        .containsExactly("tags for `GET` operation are missing");
    final String tagged =
        "openapi: 3.0.3\ntags:\n  - name: a\npaths:\n" + String.format(GET, "      tags: [a, b]\n");
    assertThat(RuleFixtures.run("operation-tag-defined", tagged))
        .extracting(OpenApiFinding::message)
        .containsExactly("tag `b` for `GET` operation is not defined as a global tag");
    assertThat(RuleFixtures.run("operation-singular-tag", tagged))
        .extracting(OpenApiFinding::message)
        .containsExactly(
            "the `get` operation at path `/a` contains more than one tag (2 is too many)'");
  }
}
