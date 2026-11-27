package dev.ward.analysis.domain.node;

import static dev.ward.analysis.domain.node.NodeListingLookups.EXITING;
import static dev.ward.analysis.domain.node.NodeListingLookups.READ;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The near variants of {@link NodeListingLookupTest}: each weakens one link of the proof. */
class NodeListingLookupNearTest {
  private static int found(final String read) {
    return NodeListingLookups.found(read, EXITING);
  }

  @Test
  void aCacheThatARequestWritesToStillReports() {
    assertThat(
        found(READ + "function leak(req) {\n  cache[req.query] = { fixes: [1], correct: 0 };\n}\n")
    ).isEqualTo(1);
  }

  @Test
  void aCacheEntryMadeOfRequestDataStillReports() {
    assertThat(
        found(READ.replace("{ fixes, correct }", "{ fixes: [req.query], correct }"))
    ).isEqualTo(1);
  }

  @Test
  void aCacheThatTheFileExposesStillReports() {
    assertThat(found(READ + "export const all = () => cache;\n")).isEqualTo(1);
    assertThat(found(READ.replace("const cache", "export const cache"))).isEqualTo(1);
  }

  @Test
  void aListingWithoutThePrefixTestStillReports() {
    assertThat(found(READ.replace("file.startsWith(`${key}_`)", "file.endsWith(`${key}_`)")))
        .isEqualTo(1);
    assertThat(found(READ.replace("file.startsWith(`${key}_`)", "file.startsWith('a_')")))
        .isEqualTo(1);
    assertThat(found(READ.replace("file.startsWith(`${key}_`)", "true"))).isEqualTo(1);
  }

  @Test
  void aListThatGrowsOutsideThePrefixTestStillReports() {
    assertThat(found(READ.replace("  cache[key] =", "  fixes.push(file);\n  cache[key] =")))
        .isEqualTo(1);
    assertThat(found(READ.replace("fixes.push(file);", "fixes.push(file);\n    fixes.push(key);")))
        .isZero();
  }

  @Test
  void aListHandedToAnotherFunctionStillReports() {
    assertThat(found(READ.replace("  cache[key] =", "  use(fixes);\n  cache[key] ="))).isEqualTo(1);
  }

  @Test
  void aListingThatNothingProvesIsADirectoryOfTheRequestStillReports() {
    assertThat(
        found(
            READ.replace("readdirSync(DIR)", "readdirSync(DIR)").replace("const files", "let files")
        )
    )
        .isEqualTo(1);
  }

  @Test
  void aFunctionThatReturnsSomethingElseStillReports() {
    assertThat(found(READ.replace("  return cache[key];\n};", "  return { fixes: [1] };\n};")))
        .isEqualTo(1);
  }
}
