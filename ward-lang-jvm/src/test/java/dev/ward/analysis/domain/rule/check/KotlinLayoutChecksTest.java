package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the ktlint checks about comments, the file and its name over real Kotlin sources. */
class KotlinLayoutChecksTest {

  private static final String MESSAGE = "m";
  private static final int DANGLING_LINE = 3;

  private static List<Integer> lines(final List<RuleMatch> matches) {
    return matches.stream().map(match -> match.span().startLine()).toList();
  }

  @Test
  void kdocIsAllowedBeforeDeclarationsAndNotInsideOrDangling() {
    final KotlinKdocPlacementCheck check = new KotlinKdocPlacementCheck(MESSAGE);

    assertThat(lines(check.matches(KotlinSourceTrees.parse("/** Doc. */\nclass A\n")))).isEmpty();
    assertThat(
            lines(
                check.matches(
                    KotlinSourceTrees.parse("class A {\n    /** Doc. */\n    val x = 1\n}\n"))))
        .isEmpty();
    assertThat(
            lines(
                check.matches(
                    KotlinSourceTrees.parse("fun f() {\n    /** Local. */\n    println()\n}\n"))))
        .containsExactly(2);
    assertThat(lines(check.matches(KotlinSourceTrees.parse("/** Dangling. */\n"))))
        .containsExactly(1);
    assertThat(
            lines(check.matches(KotlinSourceTrees.parse("class A(/** Inside. */ val x: Int)\n"))))
        .isEmpty();
  }

  @Test
  void kdocAfterAPackageLineOrABlankLineStillDocumentsTheDeclaration() {
    final KotlinKdocPlacementCheck check = new KotlinKdocPlacementCheck(MESSAGE);

    assertThat(lines(check.matches(KotlinSourceTrees.parse("package a\n\n/** Doc. */\nclass A\n"))))
        .isEmpty();
    assertThat(
            lines(
                check.matches(
                    KotlinSourceTrees.parse(
                        "class A {\n  val x = 1\n\n  /** Doc. */\n  fun f() {}\n}\n"))))
        .isEmpty();
    assertThat(
            lines(
                check.matches(
                    KotlinSourceTrees.parse("class A {\n  val x = 1\n  /** Dangling. */\n}\n"))))
        .containsExactly(DANGLING_LINE);
  }

  @Test
  void kdocInsideADeclarationIsReported() {
    final KotlinKdocPlacementCheck check = new KotlinKdocPlacementCheck(MESSAGE);

    assertThat(lines(check.matches(KotlinSourceTrees.parse("fun /** Misplaced. */ f() {}\n"))))
        .containsExactly(1);
  }

  @Test
  void consecutiveCommentsFollowTheKtlintOrder() {
    final KotlinConsecutiveCommentsCheck check = new KotlinConsecutiveCommentsCheck(MESSAGE);

    assertThat(lines(check.matches(KotlinSourceTrees.parse("/** a */\n/** b */\nclass A\n"))))
        .containsExactly(2);
    assertThat(lines(check.matches(KotlinSourceTrees.parse("// a\n// b\nclass A\n")))).isEmpty();
    assertThat(lines(check.matches(KotlinSourceTrees.parse("/* a */\n/* b */\nclass A\n"))))
        .containsExactly(2);
    assertThat(lines(check.matches(KotlinSourceTrees.parse("/* a */\n\n/** b */\nclass A\n"))))
        .isEmpty();
    assertThat(lines(check.matches(KotlinSourceTrees.parse("// a\n/* b */\nclass A\n"))))
        .containsExactly(2);
    assertThat(lines(check.matches(KotlinSourceTrees.parse("// a\n\n/* b */\nclass A\n"))))
        .isEmpty();
  }

  @Test
  void singleLineBlockCommentsThatEndALineAreReported() {
    final KotlinSingleLineBlockCommentCheck check = new KotlinSingleLineBlockCommentCheck(MESSAGE);

    assertThat(lines(check.matches(KotlinSourceTrees.parse("/* a */\nclass A\n"))))
        .containsExactly(1);
    assertThat(lines(check.matches(KotlinSourceTrees.parse("class A { /* a */ }\n")))).isEmpty();
    assertThat(lines(check.matches(KotlinSourceTrees.parse("class A\n/* a\n b */\n")))).isEmpty();
    assertThat(lines(check.matches(KotlinSourceTrees.parse("class A\n/* a */"))))
        .containsExactly(2);
  }

  @Test
  void aFileWithoutDeclarationsIsEmpty() {
    final KotlinEmptyFileCheck check = new KotlinEmptyFileCheck(MESSAGE);

    assertThat(lines(check.matches(KotlinSourceTrees.parse("package a\n\nimport b.C\n"))))
        .containsExactly(1);
    assertThat(lines(check.matches(KotlinSourceTrees.parse("class A\n")))).isEmpty();
    assertThat(lines(check.matches(KotlinSourceTrees.parse("@file:JvmName(\"X\")\n")))).isEmpty();
  }
}
