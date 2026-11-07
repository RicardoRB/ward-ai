package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class AnnotationUseStyleCheckTest {

  private static final String ANNOTATION = "annotation";
  private static final String ARGUMENTS = "annotationFieldValues";
  private static final String ALWAYS = "always";
  private static final String NEVER = "never";
  private static final String IGNORE = "ignore";
  private static final String EXPANDED = "expanded";
  private static final String COMPACT = "compact";
  private static final String COMPACT_NO_ARRAY = "compact-no-array";
  private static final String MESSAGE = "message";

  @Test
  void checksTheParenthesesOfAnAnnotationWithoutElements() {
    assertThat(run(ALWAYS, IGNORE, IGNORE, annotation(null))).hasSize(1);
    assertThat(run(ALWAYS, IGNORE, IGNORE, annotation("()"))).isEmpty();
    assertThat(run(NEVER, IGNORE, IGNORE, annotation("()"))).hasSize(1);
    assertThat(run(NEVER, IGNORE, IGNORE, annotation(null))).isEmpty();
    assertThat(run(NEVER, IGNORE, IGNORE, annotation("(1)"))).isEmpty();
  }

  @Test
  void checksNamedElementsWhenExpandedIncludingQuotedAndNestedValues() {
    assertThat(run(IGNORE, EXPANDED, IGNORE, annotation("(1)"))).hasSize(1);
    assertThat(run(IGNORE, EXPANDED, IGNORE, annotation("(value = 1)"))).isEmpty();
    assertThat(run(IGNORE, EXPANDED, IGNORE, annotation("(a = 1, 2)"))).hasSize(1);
    assertThat(run(IGNORE, EXPANDED, IGNORE, annotation("()"))).isEmpty();
    assertThat(run(IGNORE, EXPANDED, IGNORE, annotation("(\"a,b\")"))).hasSize(1);
    assertThat(run(IGNORE, EXPANDED, IGNORE, annotation("(x = {1, 2})"))).isEmpty();
    assertThat(run(IGNORE, EXPANDED, IGNORE, annotation("(x = f(1, 2))"))).isEmpty();
  }

  @Test
  void checksTheSingleElementStyles() {
    assertThat(run(IGNORE, COMPACT, IGNORE, annotation("(value = 1)"))).hasSize(1);
    assertThat(run(IGNORE, COMPACT, IGNORE, annotation("(1)"))).isEmpty();
    assertThat(run(IGNORE, COMPACT, IGNORE, annotation("(a = 1, b = 2)"))).isEmpty();
    assertThat(run(IGNORE, COMPACT_NO_ARRAY, IGNORE, annotation("({1})"))).hasSize(1);
    assertThat(run(IGNORE, COMPACT_NO_ARRAY, IGNORE, annotation("({1, 2})"))).isEmpty();
    assertThat(run(IGNORE, COMPACT_NO_ARRAY, IGNORE, annotation("({})"))).isEmpty();
    assertThat(run(IGNORE, COMPACT_NO_ARRAY, IGNORE, annotation("(value = {1})"))).hasSize(1);
  }

  @Test
  void checksTheTrailingCommaOfAnArray() {
    assertThat(run(IGNORE, IGNORE, ALWAYS, annotation("({1, 2})"))).hasSize(1);
    assertThat(run(IGNORE, IGNORE, ALWAYS, annotation("({1, 2,})"))).isEmpty();
    assertThat(run(IGNORE, IGNORE, ALWAYS, annotation("(1)"))).isEmpty();
    assertThat(run(IGNORE, IGNORE, NEVER, annotation("({1, 2,})"))).hasSize(1);
    assertThat(run(IGNORE, IGNORE, NEVER, annotation("({1, 2})"))).isEmpty();
    assertThat(run(IGNORE, IGNORE, IGNORE, annotation("(1)"))).isEmpty();
  }

  @Test
  void rejectsAnUnknownOptionValue() {
    assertThatThrownBy(() -> new AnnotationUseStyleCheck("sometimes", IGNORE, IGNORE, MESSAGE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closing-parens");
    assertThatThrownBy(() -> new AnnotationUseStyleCheck(IGNORE, "loose", IGNORE, MESSAGE))
        .hasMessageContaining("element-style");
    assertThatThrownBy(() -> new AnnotationUseStyleCheck(IGNORE, IGNORE, "maybe", MESSAGE))
        .hasMessageContaining("trailing-array-comma");
  }

  private static java.util.List<dev.ward.analysis.domain.rule.RuleMatch> run(
      final String closing, final String element, final String trailing, final SyntaxNode node) {
    return unitMatches(new AnnotationUseStyleCheck(closing, element, trailing, MESSAGE), node);
  }

  private static SyntaxNode annotation(final String arguments) {
    if (arguments == null) {
      return TreeBuilders.n(ANNOTATION, TreeBuilders.t("'@'", "@"), TreeBuilders.id("Tag"));
    }
    return TreeBuilders.n(
        ANNOTATION,
        TreeBuilders.t("'@'", "@"),
        TreeBuilders.id("Tag"),
        TreeBuilders.t(ARGUMENTS, arguments));
  }
}
