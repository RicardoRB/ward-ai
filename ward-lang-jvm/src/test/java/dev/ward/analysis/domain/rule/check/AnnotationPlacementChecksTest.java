package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AnnotationPlacementChecksTest {
  private static final int NUMBER_4 = 4;
  private static final int NUMBER_5 = 5;
  private static final int NUMBER_20 = 20;

  private static final String MESSAGE = "message";
  private static final String ANNOTATION = "annotation";
  private static final String MODIFIER = "modifier";
  private static final String PUBLIC = "public";
  private static final String PRIVATE = "private";
  private static final String BODY = "classBodyDeclaration";
  private static final String ARGUMENTS = "annotationFieldValues";
  private static final String DEPRECATED = "@Deprecated";
  private static final String DEPRECATED_NAME = "Deprecated";
  private static final int SAME_LINE_TOKEN_COLUMN = NUMBER_5;
  private static final int ACCESS_COLUMN = NUMBER_20;

  @Test
  void reportsASameLineDeclarationAfterASingleAnnotationUnlessAllowed() {
    final SyntaxNode plain = tok(ANNOTATION, "@A", 1, 0);
    assertThat(location(true, false, plain, 1)).isEmpty();
    assertThat(location(false, false, plain, 1)).hasSize(1);
    assertThat(location(false, false, plain, 2)).isEmpty();
    final SyntaxNode parameterized =
        group(ANNOTATION, tok("'@'", "@", 1, 0), tok(ARGUMENTS, "(x)", 1, 1));
    assertThat(location(false, true, parameterized, 1)).isEmpty();
    assertThat(location(false, false, parameterized, 1)).hasSize(1);
    final SyntaxNode notModifier =
        group(BODY, tok(ANNOTATION, "@A", 1, 0), tok(PUBLIC, PUBLIC, 1, NUMBER_4));
    assertThat(unitMatches(new AnnotationLocationCheck(false, false, MESSAGE), notModifier))
        .isEmpty();
    assertThat(
            unitMatches(new AnnotationLocationCheck(false, false, MESSAGE), group(MODIFIER, plain)))
        .isEmpty();
  }

  @Test
  void reportsAWatchedAnnotationOutsideTheVisibilityAndMatchesQualifiedNames() {
    assertThat(visibility(Set.of(DEPRECATED_NAME), watched(DEPRECATED, PRIVATE))).hasSize(1);
    assertThat(visibility(Set.of(DEPRECATED_NAME), watched(DEPRECATED, PUBLIC))).isEmpty();
    assertThat(visibility(Set.of(DEPRECATED_NAME), watched(DEPRECATED, null))).hasSize(1);
    assertThat(visibility(Set.of("java.lang.Deprecated"), watched(DEPRECATED, PRIVATE))).hasSize(1);
    assertThat(visibility(Set.of(DEPRECATED_NAME), watched("@Override", PRIVATE))).isEmpty();
    assertThat(visibility(Set.of(DEPRECATED_NAME), watched(DEPRECATED_NAME, PRIVATE))).isEmpty();
    assertThat(visibility(Set.of(DEPRECATED_NAME), watched("@Deprecated(since = \"1\")", PRIVATE)))
        .hasSize(1);
  }

  @Test
  void reportsAnAnnotationFollowedByANewLineAndIgnoresOtherHolders() {
    assertThat(
            sameLine(
                group(
                    MODIFIER,
                    tok(ANNOTATION, "@A", 1, 0),
                    tok(PUBLIC, PUBLIC, 2, SAME_LINE_TOKEN_COLUMN))))
        .hasSize(1);
    assertThat(
            sameLine(
                group(MODIFIER, tok(ANNOTATION, "@A", 1, 0), tok(PUBLIC, PUBLIC, 1, NUMBER_4))))
        .isEmpty();
    assertThat(
            sameLine(
                group(
                    BODY,
                    tok(ANNOTATION, "@A", 1, 0),
                    tok(PUBLIC, PUBLIC, 2, SAME_LINE_TOKEN_COLUMN))))
        .isEmpty();
  }

  @Test
  void reportsASuppressWarningsNameThatMatchesTheFormat() {
    assertThat(suppress("unchecked", suppressWarnings("unchecked"))).hasSize(1);
    assertThat(suppress("^rawtypes$", suppressWarnings("unchecked"))).isEmpty();
    assertThat(suppress("unchecked", tok(ANNOTATION, "@Other", 1, 0))).isEmpty();
    final SyntaxNode stray =
        group(
            ANNOTATION,
            tok("'@'", "@", 1, 0),
            tok("identifier", "SuppressWarnings", 1, 1),
            tok("stringLiteral", "\"", 1, ACCESS_COLUMN - 2),
            tok("identifier", "x", 1, ACCESS_COLUMN - 1));
    assertThat(suppress("x", stray)).isEmpty();
  }

  private static List<RuleMatch> location(
      final boolean singleParameterless,
      final boolean parameterized,
      final SyntaxNode annotation,
      final int line) {
    final SyntaxNode decl =
        group(BODY, group(MODIFIER, annotation), tok(PUBLIC, PUBLIC, line, SAME_LINE_TOKEN_COLUMN));
    return unitMatches(
        new AnnotationLocationCheck(singleParameterless, parameterized, MESSAGE), decl);
  }

  private static List<RuleMatch> visibility(final Set<String> annotations, final SyntaxNode decl) {
    return unitMatches(
        new AnnotatedDeclarationVisibilityCheck(annotations, Set.of(PUBLIC), MESSAGE), decl);
  }

  private static List<RuleMatch> sameLine(final SyntaxNode decl) {
    return unitMatches(new AnnotationOnSameLineCheck(MESSAGE), decl);
  }

  private static List<RuleMatch> suppress(final String format, final SyntaxNode annotation) {
    return unitMatches(new SuppressWarningsCheck(Pattern.compile(format), MESSAGE), annotation);
  }

  private static SyntaxNode watched(final String annotationText, final String access) {
    final SyntaxNode annotation = group(MODIFIER, tok(ANNOTATION, annotationText, 1, 0));
    if (access == null) {
      return group(BODY, annotation);
    }
    return group(BODY, annotation, group(MODIFIER, tok(MODIFIER, access, 1, ACCESS_COLUMN)));
  }

  private static SyntaxNode suppressWarnings(final String name) {
    return group(
        ANNOTATION,
        tok("'@'", "@", 1, 0),
        tok("identifier", "SuppressWarnings", 1, 1),
        tok("stringLiteral", "\"" + name + "\"", 1, ACCESS_COLUMN - 2));
  }
}
