package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tells the type of the receiver of a call from what the source states: the type of an associated
 * function call such as {@code Command::new(..)}, a builder chain on it, and a local or parameter
 * whose declared type or creation names a type. A type is its last path segment without generic
 * arguments, as the standard library documentation names it. shortcut: the type of any other
 * receiver is unknown and a method that returns another type keeps the receiver's type; upgrade
 * with type facts.
 */
final class RustReceivers {
  private static final String PATH_SEPARATOR = "::";
  private static final int MIN_PATH = 2;
  private static final String LET = "letStatement";
  private static final String PARAM = "functionParamPattern";

  private RustReceivers() {}

  /** The type of each local and parameter of a body whose type is stated, by name. */
  static Map<String, String> locals(final SyntaxNode parameters, final SyntaxNode body) {
    final Map<String, String> types = new HashMap<>();
    if (parameters != null) {
      collect(parameters, types);
    }
    collect(body, types);
    return types;
  }

  private static void collect(final SyntaxNode node, final Map<String, String> types) {
    if (LET.equals(node.kind())) {
      bind(node, types, RustTrees.child(node, RustKinds.PATTERN_NO_ALT));
    } else if (PARAM.equals(node.kind())) {
      bind(node, types, RustTrees.child(node, RustKinds.PATTERN));
    }
    node.children().forEach(child -> collect(child, types));
  }

  private static void bind(
      final SyntaxNode node,
      final Map<String,
      String> types,
      final SyntaxNode pattern
  ) {
    final SyntaxNode name = pattern == null ? null : RustTrees.find(pattern, RustKinds.IDENTIFIER);
    if (name == null) {
      return;
    }
    declared(node).or(() -> created(node, types)).ifPresent(
        type -> types.put(RustTrees.name(name), type)
    );
  }

  private static Optional<String> declared(final SyntaxNode node) {
    final SyntaxNode type = RustTrees.child(node, "type_");
    final SyntaxNode segment = type == null ? null : RustTrees.find(type, "typePathSegment");
    final SyntaxNode name =
        segment == null ? null : RustTrees.find(segment, RustKinds.IDENTIFIER);
    return name == null ? Optional.empty() : Optional.of(RustTrees.name(name));
  }

  private static Optional<String> created(final SyntaxNode node, final Map<String, String> types) {
    final List<SyntaxNode> parts = node.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (RustTrees.isToken(parts.get(index), RustKinds.EQUALS)) {
        return of(parts.get(index + 1), types);
      }
    }
    return Optional.empty();
  }

  /** The type of the value of {@code expression}, when it is known. */
  static Optional<String> of(final SyntaxNode expression, final Map<String, String> types) {
    final List<SyntaxNode> parts = expression.children();
    if (parts.isEmpty() || !RustKinds.EXPRESSION.equals(expression.kind())) {
      return Optional.empty();
    }
    final SyntaxNode first = parts.get(0);
    if (RustKinds.EXPRESSION.equals(first.kind()) && parts.size() > 1) {
      return viaFirst(expression, first, types);
    }
    return "pathExpression".equals(first.kind())
        ? Optional.ofNullable(types.get(RustTrees.text(first)))
        : Optional.empty();
  }

  private static Optional<String> viaFirst(
      final SyntaxNode expression,
      final SyntaxNode first,
      final Map<String,
      String> types
  ) {
    final List<SyntaxNode> parts = expression.children();
    final boolean call = RustTrees.isToken(parts.get(1), "'('");
    if (call) {
      return calleeType(RustTrees.text(first));
    }
    final boolean method = RustTrees.isToken(parts.get(1), RustKinds.DOT)
        && parts.size() > 2
        && RustKinds.PATH_EXPR_SEGMENT.equals(parts.get(2).kind());
    final boolean unwrap = RustTrees.isToken(parts.get(1), RustKinds.QUESTION);
    return method || unwrap ? of(first, types) : Optional.empty();
  }

  /** The type that an associated function path names: {@code Command} in {@code Command::new}. */
  static Optional<String> calleeType(final String callee) {
    final String[] segments = callee.replaceAll("<[^<>]*>", "").split(PATH_SEPARATOR);
    if (segments.length < MIN_PATH) {
      return Optional.empty();
    }
    final String type = segments[segments.length - 2];
    return !type.isEmpty() && Character.isUpperCase(type.charAt(0))
        ? Optional.of(type)
        : Optional.empty();
  }
}
