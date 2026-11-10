package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads of a Java syntax tree that the nullness checks share: annotations, kinds and operators. */
final class NullSyntax {

  /** The kinds of node that hold the annotations of a declaration. */
  static final Set<String> HOLDERS =
      Set.of("modifier", "classOrInterfaceModifier", "variableModifier");

  /** The kinds of type declaration the checks read. */
  static final Set<String> TYPES = Set.of("classDeclaration", "interfaceDeclaration");

  /** The number of children of a binary expression: left side, operator, right side. */
  static final int BINARY = 3;

  private static final Set<String> NESTED = Set.of("lambdaExpression", "classBody");
  private static final String ANNOTATION = "annotation";
  private static final String EXPRESSION = "expression";

  private NullSyntax() {}

  /** The annotations written directly in {@code holder}, inside its modifiers if it has any. */
  static List<SyntaxNode> annotations(final SyntaxNode holder) {
    final List<SyntaxNode> out = new ArrayList<>();
    for (final SyntaxNode child : holder.children()) {
      if (ANNOTATION.equals(child.kind())) {
        out.add(child);
      } else if (HOLDERS.contains(child.kind())) {
        out.addAll(annotations(child));
      }
    }
    return out;
  }

  /** Returns the notes of {@code nodes}. */
  static NullModel.Notes notes(final List<SyntaxNode> nodes) {
    final Set<String> names = new java.util.LinkedHashSet<>();
    nodes.forEach(node -> names.add(simpleName(node)));
    return new NullModel.Notes(List.copyOf(nodes), names);
  }

  static NullModel.Notes merge(final NullModel.Notes notes, final List<SyntaxNode> more) {
    final List<SyntaxNode> all = new ArrayList<>(notes.nodes());
    all.addAll(more);
    return notes(all);
  }

  /** The simple name of the annotation {@code node}. */
  static String simpleName(final SyntaxNode node) {
    final String name =
        first(node, "qualifiedName").map(SyntaxWalk::text).orElse(SyntaxWalk.text(node));
    return name.substring(name.lastIndexOf('.') + 1);
  }

  /** Tells whether the type is a primitive that is not an array. */
  static boolean isPrimitive(final SyntaxNode type) {
    return first(type, "primitiveType").isPresent() && !isArray(type);
  }

  /** Tells whether the type has array brackets. */
  static boolean isArray(final SyntaxNode type) {
    return type.children().stream().anyMatch(child -> "[".equals(SyntaxWalk.text(child)));
  }

  /** Returns the first direct child of {@code kind}. */
  static Optional<SyntaxNode> first(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }

  /**
   * Returns the nodes of the given kinds in {@code body}, not entering lambdas or local classes.
   */
  static List<SyntaxNode> scan(final SyntaxNode body, final Set<String> kinds) {
    final List<SyntaxNode> out = new ArrayList<>();
    if (body != null) {
      scan(body, kinds, out);
    }
    return out;
  }

  private static void scan(
      final SyntaxNode node, final Set<String> kinds, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (kinds.contains(child.kind())) {
        out.add(child);
      }
      if (!NESTED.contains(child.kind()) && !TYPES.contains(child.kind())) {
        scan(child, kinds, out);
      }
    }
  }

  /** Returns the expression nodes in {@code body}. */
  static List<SyntaxNode> expressions(final SyntaxNode body) {
    return scan(body, Set.of(EXPRESSION));
  }

  /** The operator of a binary expression: its middle child, or empty. */
  static String operator(final SyntaxNode expression) {
    final List<SyntaxNode> parts = expression.children();
    return parts.size() == BINARY && EXPRESSION.equals(parts.get(0).kind())
        ? SyntaxWalk.text(parts.get(1))
        : "";
  }

  /** Tells whether the expression is the literal {@code null}. */
  static boolean isNull(final SyntaxNode expression) {
    return "null".equals(SyntaxWalk.text(expression));
  }

  /** The text of the expression without a leading {@code this.}. */
  static String name(final SyntaxNode expression) {
    final String text = SyntaxWalk.text(expression);
    return text.startsWith("this.") ? text.substring("this.".length()) : text;
  }
}
