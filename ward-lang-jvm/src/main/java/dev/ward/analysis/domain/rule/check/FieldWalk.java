package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Walks a member of a class in source order and records the uses of the class's fields. The value
 * of an assignment is read before its target is written, and a compound assignment or an increment
 * reads before it writes.
 */
final class FieldWalk {

  private static final String EXPRESSION = "expression";
  private static final String THIS = "this.";
  private static final String NULL = "null";
  private static final int POSTFIX_OR_PREFIX = 2;
  private static final Set<String> STEPS = Set.of("'++'", "'--'");

  private FieldWalk() {}

  /**
   * Adds the field accesses below {@code node} to {@code out}.
   *
   * @param node the node to walk
   * @param parent the parent of {@code node}, or null at the root
   * @param fieldNames the names of the fields of the class
   * @param locals the names the member declares itself, which hide fields of the same name
   * @param out receives the accesses
   */
  static void walk(
      final SyntaxNode node,
      final SyntaxNode parent,
      final Set<String> fieldNames,
      final Set<String> locals,
      final List<FieldAccesses.Access> out) {
    if (Assignments.isAssignment(node)) {
      assignment(node, fieldNames, locals, out);
    } else if (isStep(node)) {
      step(node, fieldNames, locals, out);
    } else if (EXPRESSION.equals(node.kind())
        && named(SyntaxWalk.text(node), fieldNames, locals).isPresent()) {
      final String name = named(SyntaxWalk.text(node), fieldNames, locals).orElseThrow();
      out.add(
          new FieldAccesses.Access(
              name, node, FieldAccesses.Use.READ, selectsMember(node, parent)));
    } else {
      node.children().forEach(child -> walk(child, node, fieldNames, locals, out));
    }
  }

  private static void assignment(
      final SyntaxNode node,
      final Set<String> fieldNames,
      final Set<String> locals,
      final List<FieldAccesses.Access> out) {
    final SyntaxNode target = node.children().get(0);
    final SyntaxNode value = node.children().get(2);
    final Optional<String> name = named(SyntaxWalk.text(target), fieldNames, locals);
    if (name.isEmpty()) {
      walk(target, node, fieldNames, locals, out);
      walk(value, node, fieldNames, locals, out);
      return;
    }
    if (!"'='".equals(node.children().get(1).kind())) {
      out.add(new FieldAccesses.Access(name.get(), target, FieldAccesses.Use.READ, false));
    }
    walk(value, node, fieldNames, locals, out);
    final boolean isNull = NULL.equals(SyntaxWalk.text(value));
    out.add(
        new FieldAccesses.Access(
            name.get(),
            target,
            isNull ? FieldAccesses.Use.WRITE_NULL : FieldAccesses.Use.WRITE,
            false));
  }

  private static boolean isStep(final SyntaxNode node) {
    return EXPRESSION.equals(node.kind())
        && node.children().size() == POSTFIX_OR_PREFIX
        && node.children().stream().anyMatch(child -> STEPS.contains(child.kind()))
        && node.children().stream().anyMatch(child -> EXPRESSION.equals(child.kind()));
  }

  private static void step(
      final SyntaxNode node,
      final Set<String> fieldNames,
      final Set<String> locals,
      final List<FieldAccesses.Access> out) {
    final SyntaxNode operand =
        node.children().stream().filter(c -> EXPRESSION.equals(c.kind())).findFirst().orElseThrow();
    final Optional<String> name = named(SyntaxWalk.text(operand), fieldNames, locals);
    if (name.isEmpty()) {
      walk(operand, node, fieldNames, locals, out);
      return;
    }
    out.add(new FieldAccesses.Access(name.get(), operand, FieldAccesses.Use.READ, false));
    out.add(new FieldAccesses.Access(name.get(), operand, FieldAccesses.Use.WRITE, false));
  }

  /** Returns the field that {@code text} names, as {@code x} or {@code this.x}. */
  private static Optional<String> named(
      final String text, final Set<String> fieldNames, final Set<String> locals) {
    if (text.startsWith(THIS)) {
      final String name = text.substring(THIS.length());
      return fieldNames.contains(name) ? Optional.of(name) : Optional.empty();
    }
    return fieldNames.contains(text) && !locals.contains(text)
        ? Optional.of(text)
        : Optional.empty();
  }

  private static boolean selectsMember(final SyntaxNode node, final SyntaxNode parent) {
    return parent != null
        && EXPRESSION.equals(parent.kind())
        && parent.children().size() >= 2
        && parent.children().get(0).equals(node)
        && ".".equals(SyntaxWalk.text(parent.children().get(1)));
  }
}
