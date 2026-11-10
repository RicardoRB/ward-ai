package dev.ward.analysis.domain.node.lint;

import static dev.ward.analysis.domain.node.lint.LintFixtures.positions;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The syntax-only checks whose options and redundant-member rules are tested. */
class SyntaxOptionChecksTest {

  private static final String MESSAGE = "message";
  private static final int MIN_LENGTH = 3;

  @Test
  void banTsCommentReportsBannedDirectivesAndShortDescriptions() {
    final String text =
        "// @ts-ignore\n// @ts-expect-error\nconst a = 1; // @ts-expect-error: the value is fixed here\n"
            + "/* @ts-ignore */\nconst b = 2;\n";

    assertThat(positions(new BanTsCommentCheck(defaults(), MIN_LENGTH, "", MESSAGE), text))
        .containsExactly("1:1", "2:1", "4:1");
  }

  @Test
  void banTsCommentDescriptionFormatReplacesTheMinimumLength() {
    final String text =
        "// @ts-expect-error: because it is fixed\nconst a = 1;\n"
            + "// @ts-expect-error: TS2322 because the value is fixed\nconst b = 2;\n"
            + "// @ts-expect-error\nconst c = 3;\n";

    assertThat(
            positions(
                new BanTsCommentCheck(defaults(), MIN_LENGTH, "^: TS\\d+ because .+$", MESSAGE),
                text))
        .containsExactly("1:1", "5:1");
  }

  @Test
  void banTsCommentReadsEachPragmaWithItsOwnOption() {
    assertThat(
            positions(
                new BanTsCommentCheck(defaults(), MIN_LENGTH, "", MESSAGE),
                "// @ts-check\n// @ts-nocheck\nconst a = 1;\n// plain comment\n"))
        .containsExactly("2:1");
  }

  @Test
  void explicitAnyIsReportedAndRestParameterArraysAreIgnoredWhenAllowed() {
    final String text =
        "let a: any = 1;\nfunction f(...args: any[]) {}\n" + "function g(x: Array<any>): void {}\n";

    assertThat(positions(new NoExplicitAnyCheck(false, MESSAGE), text))
        .containsExactly("1:8", "2:21", "3:21");
    assertThat(positions(new NoExplicitAnyCheck(true, MESSAGE), text))
        .containsExactly("1:8", "3:21");
  }

  @Test
  void redundantConstituentsOverrideOrAreOverriddenInUnionsAndIntersections() {
    final String text =
        "type A = 'a' | string;\ntype B = string | any;\ntype C = never | number;\n"
            + "type D = string & 'a';\ntype E = 'a' | 'b';\ntype F = unknown & string;\n";

    assertThat(positions(new RedundantConstituentsCheck(MESSAGE), text))
        .containsExactly("1:10", "2:19", "3:10", "4:10", "6:10");
  }

  @Test
  void redundantConstituentsNeedAnotherMemberBeforeAnyIsReported() {
    assertThat(positions(new RedundantConstituentsCheck(MESSAGE), "type A = any;\n")).isEmpty();
  }

  private static Map<String, String> defaults() {
    return Map.of(
        "ts-ignore",
        "true",
        "ts-expect-error",
        "allow-with-description",
        "ts-check",
        "false",
        "ts-nocheck",
        "true");
  }

  @Test
  void tripleSlashReferencesFollowTheirOptionsAndTheImportsOfTheFile() {
    final String text =
        "/// <reference path=\"a.d.ts\" />\n/// <reference types=\"node\" />\n"
            + "/// <reference types=\"vite/client\" />\nimport \"node\";\n";

    assertThat(
            positions(
                new TripleSlashReferenceCheck("always", "never", "prefer-import", MESSAGE), text))
        .containsExactly("1:1", "2:1");
    assertThat(positions(new TripleSlashReferenceCheck("always", "always", "never", MESSAGE), text))
        .containsExactly("2:1", "3:1");
  }
}
