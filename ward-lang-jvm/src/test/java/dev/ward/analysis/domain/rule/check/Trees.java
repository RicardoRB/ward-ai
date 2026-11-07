package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builders for the hand-made syntax trees the check tests run over. */
final class Trees {

  static final String COMMENT = "comment";
  static final String IDENTIFIER = "identifier";
  static final int METHOD_LIMIT = 60;
  static final int SOURCE_LENGTH = 100;
  static final int DESCENDANT_LIMIT = 4;
  static final int DECLARATION_END = 40;
  static final int IMPORT_KEYWORD_LENGTH = 7;
  static final int NAME_OFFSET_FROM_END = 5;

  private static final Set<String> MODIFIERS = Set.of("modifier", "annotation");

  private Trees() {}

  /** Filters declarations of {@code kinds} by modifiers, with the usual modifier kinds. */
  static DeclarationFilter filter(
      final Set<String> kinds, final Optional<String> required, final Optional<String> excluded) {
    return new DeclarationFilter(kinds, MODIFIERS, required, excluded);
  }

  /** A token with text, one character per column, starting at {@code offset}. */
  static SyntaxNode leaf(final String kind, final String text, final int offset) {
    return new SyntaxNode(
        kind,
        new Span(1, offset + 1, 1, offset + text.length(), offset, offset + text.length()),
        Map.of("text", text),
        List.of());
  }

  /** A rule node on line 1 through {@code endLine}, covering {@code start} to {@code end}. */
  static SyntaxNode node(
      final String kind,
      final int start,
      final int end,
      final int endLine,
      final SyntaxNode... children) {
    return new SyntaxNode(kind, span(start, end, 1, endLine), Map.of(), List.of(children));
  }

  /** Puts a declaration under a compilation unit, as every real tree has a root above it. */
  static SyntaxNode unit(final SyntaxNode declaration) {
    return node("compilationUnit", 0, SOURCE_LENGTH, 1, declaration);
  }

  /** An import of {@code name} at {@code offset}, with its name as the last identifier. */
  static SyntaxNode importNode(final String name, final int offset) {
    final int end = offset + 20;
    final String simple = name.substring(name.lastIndexOf('.') + 1);
    return node(
        "importDeclaration",
        offset,
        end,
        1,
        leaf("'import'", "import", offset),
        node(
            "qualifiedName",
            offset + IMPORT_KEYWORD_LENGTH,
            end - 1,
            1,
            leaf(IDENTIFIER, simple, end - NAME_OFFSET_FROM_END)));
  }

  /**
   * A compilation unit whose children are an optional comment and a type declaration. The type
   * declaration starts with its public modifier, and the class starts at the keyword after it.
   */
  static SyntaxNode documentedClass(final boolean withComment) {
    final SyntaxNode clazz =
        node(
            "classDeclaration",
            12,
            DECLARATION_END,
            2,
            leaf("CLASS", "class", 12),
            leaf(IDENTIFIER, "Order", 18));
    final SyntaxNode wrapper =
        node(
            "typeDeclaration",
            7,
            DECLARATION_END,
            2,
            node("modifier", 7, 13, 2, leaf("'public'", "public", 7)),
            clazz);
    final List<SyntaxNode> children = new ArrayList<>();
    if (withComment) {
      children.add(leaf(COMMENT, "/** Doc. */", 0));
    }
    children.add(wrapper);
    return new SyntaxNode("compilationUnit", span(0, DECLARATION_END, 1, 2), Map.of(), children);
  }

  /** The messages of {@code matches}, in order. */
  static List<String> messages(final List<RuleMatch> matches) {
    return matches.stream().map(RuleMatch::message).toList();
  }

  private static Span span(final int start, final int end, final int startLine, final int endLine) {
    return new Span(startLine, 1, endLine, 1, start, end);
  }
}
