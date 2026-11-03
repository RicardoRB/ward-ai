package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChecksTest {

  private static final String COMMENT = "comment";
  private static final Set<String> MODIFIERS = Set.of("modifier", "annotation");

  @Test
  void linesCheckFailsOnlyAboveTheLimit() {
    final SyntaxNode body = node("methodBody", 0, 100, 1, 61);
    final LinesCheck check = new LinesCheck(Set.of("methodBody"), 60, "{value} > {max}");

    assertThat(messages(check.matches(body))).containsExactly("61 > 60");
    assertThat(check.matches(node("methodBody", 0, 100, 1, 60))).isEmpty();
  }

  @Test
  void descendantCountAddsTheBaseAndFailsAboveTheLimit() {
    final SyntaxNode method =
        node(
            "methodDeclaration",
            0,
            40,
            1,
            1,
            leaf("'if'", "if", 0),
            leaf("'for'", "for", 4),
            leaf("'if'", "if", 8));

    assertThat(
            messages(
                new DescendantCountCheck(
                        Set.of("methodDeclaration"),
                        Set.of("'if'", "'for'"),
                        1,
                        2,
                        "{value} > {max}")
                    .matches(method)))
        .containsExactly("4 > 2");
    assertThat(
            new DescendantCountCheck(
                    Set.of("methodDeclaration"), Set.of("'if'", "'for'"), 1, 4, "{value}")
                .matches(method))
        .isEmpty();
  }

  @Test
  void cognitiveComplexityIncreasesWithNesting() {
    final SyntaxNode inner = node("statement", 6, 20, 1, 1, leaf("'if'", "if", 6));
    final SyntaxNode outer =
        node("ifStatement", 0, 20, 1, 1, leaf("'if'", "if", 0), leaf("'&&'", "&&", 3), inner);
    final CognitiveComplexityCheck check =
        new CognitiveComplexityCheck(
            Set.of("ifStatement"), Set.of("'if'"), Set.of("'&&'"), 3, "{value} > {max}");

    // The outer if adds 1, the && adds 1, and the nested if adds 1 plus its nesting of 1.
    assertThat(messages(check.matches(outer))).containsExactly("4 > 3");
  }

  @Test
  void textPatternChecksTheNameOfEachDeclaration() {
    final SyntaxNode declaration =
        node(
            "classDeclaration",
            0,
            20,
            1,
            1,
            leaf("CLASS", "class", 0),
            leaf("identifier", "Order_Item", 6));
    final TextPatternCheck check =
        new TextPatternCheck(
            filter(Set.of("classDeclaration"), Optional.empty(), Optional.empty()),
            "identifier",
            Set.of(),
            "[A-Z][a-zA-Z0-9]*",
            "bad name {value}");

    assertThat(messages(check.matches(declaration))).containsExactly("bad name Order_Item");
  }

  @Test
  void textPatternSkipsDeclarationsWithTheExcludedModifier() {
    final SyntaxNode field =
        node(
            "classBodyDeclaration",
            0,
            25,
            1,
            1,
            node("modifier", 0, 6, 1, 1, leaf("'static'", "static", 0)),
            node("fieldDeclaration", 7, 25, 1, 1, leaf("identifier", "MAX_SIZE", 14)));
    final TextPatternCheck check =
        new TextPatternCheck(
            filter(Set.of("fieldDeclaration"), Optional.empty(), Optional.of("static")),
            "identifier",
            Set.of(),
            "[a-z][a-zA-Z0-9]*",
            "{value}");

    assertThat(check.matches(unit(field))).isEmpty();
  }

  @Test
  void textPatternRejectsMalformedRegularExpression() {
    assertThatThrownBy(
            () ->
                new TextPatternCheck(
                    filter(Set.of("classDeclaration"), Optional.empty(), Optional.empty()),
                    "identifier",
                    Set.of(),
                    "[unclosed",
                    "{value}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unreferencedNameReportsAnImportNoOneUses() {
    final SyntaxNode importUnused = importNode("java.util.Map", 0);
    final SyntaxNode importUsed = importNode("java.util.List", 30);
    final SyntaxNode use = leaf("identifier", "List", 60);
    final SyntaxNode unit = node("compilationUnit", 0, 70, 1, 3, importUnused, importUsed, use);

    assertThat(messages(new UnreferencedNameCheck("unused {value}").matches(unit)))
        .containsExactly("unused Map");
  }

  @Test
  void unreferencedNameCountsAJavadocReference() {
    final SyntaxNode importNode = importNode("java.util.List", 0);
    final SyntaxNode javadoc = leaf(COMMENT, "/** See {@link List} */", 40);
    final SyntaxNode unit = node("compilationUnit", 0, 70, 1, 3, importNode, javadoc);

    assertThat(new UnreferencedNameCheck("unused {value}").matches(unit)).isEmpty();
  }

  @Test
  void precedingCommentAcceptsADocumentedDeclaration() {
    assertThat(preceding(Optional.empty()).matches(documentedClass(true))).isEmpty();
  }

  @Test
  void precedingCommentReportsAnUndocumentedDeclaration() {
    assertThat(messages(preceding(Optional.empty()).matches(documentedClass(false))))
        .containsExactly("missing");
  }

  @Test
  void precedingCommentSkipsAnExemptAnnotation() {
    final SyntaxNode overriding =
        node(
            "classBodyDeclaration",
            0,
            30,
            1,
            1,
            node(
                "modifier",
                0,
                9,
                1,
                1,
                node(
                    "annotation",
                    0,
                    9,
                    1,
                    1,
                    leaf("'@'", "@", 0),
                    leaf("identifier", "Override", 1))),
            node("methodDeclaration", 12, 30, 1, 1, leaf("identifier", "run", 20)));
    final PrecedingCommentCheck check =
        new PrecedingCommentCheck(
            filter(Set.of("methodDeclaration"), Optional.empty(), Optional.empty()),
            Optional.of("Override"),
            "missing");

    assertThat(check.matches(unit(overriding))).isEmpty();
  }

  private static PrecedingCommentCheck preceding(final Optional<String> exempt) {
    return new PrecedingCommentCheck(
        filter(Set.of("classDeclaration"), Optional.of("public"), Optional.empty()),
        exempt,
        "missing");
  }

  private static DeclarationFilter filter(
      final Set<String> kinds, final Optional<String> required, final Optional<String> excluded) {
    return new DeclarationFilter(kinds, MODIFIERS, required, excluded);
  }

  /**
   * A compilation unit whose children are an optional comment and a type declaration. The type
   * declaration starts with its public modifier, and the class starts at the keyword after it.
   */
  private static SyntaxNode documentedClass(final boolean withComment) {
    final SyntaxNode clazz =
        node(
            "classDeclaration",
            12,
            40,
            2,
            2,
            leaf("CLASS", "class", 12),
            leaf("identifier", "Order", 18));
    final SyntaxNode wrapper =
        node(
            "typeDeclaration",
            7,
            40,
            2,
            2,
            node("modifier", 7, 13, 2, 2, leaf("'public'", "public", 7)),
            clazz);
    final List<SyntaxNode> children = new ArrayList<>();
    if (withComment) {
      children.add(leaf(COMMENT, "/** Doc. */", 0));
    }
    children.add(wrapper);
    return new SyntaxNode("compilationUnit", span(0, 40, 1, 2), Map.of(), children);
  }

  private static SyntaxNode importNode(final String name, final int offset) {
    final int end = offset + 20;
    final String simple = name.substring(name.lastIndexOf('.') + 1);
    return node(
        "importDeclaration",
        offset,
        end,
        1,
        1,
        leaf("'import'", "import", offset),
        node("qualifiedName", offset + 7, end - 1, 1, 1, leaf("identifier", simple, end - 5)));
  }

  private static SyntaxNode leaf(final String kind, final String text, final int offset) {
    return new SyntaxNode(
        kind,
        new Span(1, offset + 1, 1, offset + text.length(), offset, offset + text.length()),
        Map.of("text", text),
        List.of());
  }

  private static SyntaxNode node(
      final String kind,
      final int start,
      final int end,
      final int startLine,
      final int endLine,
      final SyntaxNode... children) {
    return new SyntaxNode(kind, span(start, end, startLine, endLine), Map.of(), List.of(children));
  }

  /** Puts a declaration under a compilation unit, as every real tree has a root above it. */
  private static SyntaxNode unit(final SyntaxNode declaration) {
    return node("compilationUnit", 0, 100, 1, 1, declaration);
  }

  private static Span span(final int start, final int end, final int startLine, final int endLine) {
    return new Span(startLine, 1, endLine, 1, start, end);
  }

  private static List<String> messages(final List<RuleMatch> matches) {
    return matches.stream().map(RuleMatch::message).toList();
  }
}
