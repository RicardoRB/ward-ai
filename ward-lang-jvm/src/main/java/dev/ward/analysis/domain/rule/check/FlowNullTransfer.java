package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Walks the statements and conditions of a basic block in evaluation order, updating what is known
 * about each local variable and reporting the null findings it meets.
 */
final class FlowNullTransfer {

  private static final String EXPRESSION = "expression";
  private static final String ASSIGN = "=";
  private static final String DOT = ".";
  private static final Set<String> NESTED = Set.of("lambdaExpression", "classBody");
  private static final int BINARY = 3;
  private static final int INDEXED = 4;

  private final FlowNullScope scope;
  private final FlowNullChecks checks;

  FlowNullTransfer(final FlowNullScope scope) {
    this.scope = scope;
    this.checks = new FlowNullChecks(scope);
  }

  /** Returns the state at the end of {@code block}, given the state {@code in} at its start. */
  FlowNullState run(final BasicBlock block, final FlowNullState in) {
    final FlowNullState state = in.copy();
    block.nodes().forEach(node -> visit(node, state));
    return state;
  }

  private void visit(final SyntaxNode node, final FlowNullState state) {
    if (NESTED.contains(node.kind())) {
      return;
    }
    switch (node.kind()) {
      case "localVariableDeclaration" -> declaration(node, state);
      case "statement" -> statement(node, state);
      case EXPRESSION -> expression(node, state);
      case "methodCall" -> FlowNullCalls.arguments(node).forEach(arg -> visit(arg, state));
      default -> node.children().forEach(child -> visit(child, state));
    }
  }

  private void declaration(final SyntaxNode node, final FlowNullState state) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(node, Set.of("variableDeclarator"))) {
      final SyntaxNode declarator = visit.node();
      final Optional<SyntaxNode> init =
          declarator.children().stream()
              .filter(child -> "variableInitializer".equals(child.kind()))
              .flatMap(child -> child.children().stream())
              .filter(child -> EXPRESSION.equals(child.kind()))
              .findFirst();
      init.ifPresent(value -> visit(value, state));
      final Optional<SyntaxNode> id =
          SyntaxWalk.firstDescendant(declarator, "identifier", Set.of("variableInitializer"));
      final FlowNullValue value =
          init.map(expr -> FlowNullValues.of(expr, state, scope)).orElse(FlowNullValue.UNKNOWN);
      id.flatMap(scope::variable).ifPresent(decl -> state.put(decl, value, false));
    }
  }

  private void statement(final SyntaxNode node, final FlowNullState state) {
    final List<SyntaxNode> parts = node.children();
    final String keyword = parts.isEmpty() ? "" : SyntaxWalk.text(parts.get(0));
    if ("return".equals(keyword) && parts.size() > 1 && EXPRESSION.equals(parts.get(1).kind())) {
      visit(parts.get(1), state);
      FlowNullSinks.returned(parts.get(1), state, scope);
    } else if ("throw".equals(keyword) && parts.size() > 1) {
      parts.stream()
          .filter(part -> EXPRESSION.equals(part.kind()))
          .findFirst()
          .ifPresent(
              part -> {
                visit(part, state);
                checks.dereference(part, "", state);
              });
    } else {
      parts.forEach(part -> visit(part, state));
    }
  }

  private void expression(final SyntaxNode node, final FlowNullState state) {
    final List<SyntaxNode> parts = node.children();
    final String operator = parts.size() == BINARY ? SyntaxWalk.text(parts.get(1)) : "";
    if (ASSIGN.equals(operator)) {
      assignment(parts.get(0), parts.get(2), state);
    } else if (DOT.equals(operator) && EXPRESSION.equals(parts.get(0).kind())) {
      member(node, state);
    } else if (parts.size() == INDEXED && "[".equals(SyntaxWalk.text(parts.get(1)))) {
      visit(parts.get(0), state);
      checks.dereference(parts.get(0), "", state);
      visit(parts.get(2), state);
    } else {
      operation(node, operator, state);
    }
  }

  private void operation(final SyntaxNode node, final String operator, final FlowNullState state) {
    final List<SyntaxNode> parts = node.children();
    parts.forEach(part -> visit(part, state));
    switch (operator) {
      case "instanceof" -> checks.instanceOf(parts.get(0), state);
      case "==", "!=" -> checks.comparison(parts, state);
      default -> FlowNullCalls.of(node).ifPresent(call -> FlowNullSinks.call(call, state, scope));
    }
  }

  private void assignment(
      final SyntaxNode target, final SyntaxNode value, final FlowNullState state) {
    visit(value, state);
    final FlowNullValue given = FlowNullValues.of(value, state, scope);
    final String name = fieldName(target);
    final Optional<Span> local =
        FlowNullValues.isName(target) ? scope.variable(target) : Optional.empty();
    if (local.isPresent()) {
      state.put(local.get(), given, false);
    } else if (name.isEmpty()) {
      visit(target, state);
    } else {
      FlowNullSinks.field(name, value, state, scope);
    }
  }

  /** The name of the field a target writes, as {@code f} or {@code this.f}; empty otherwise. */
  private String fieldName(final SyntaxNode target) {
    final String text = SyntaxWalk.text(target);
    final String name = text.startsWith("this.") ? text.substring("this.".length()) : text;
    return FlowNullValues.isName(target) || text.startsWith("this.") ? name : "";
  }

  private void member(final SyntaxNode node, final FlowNullState state) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode access = parts.get(2);
    visit(parts.get(0), state);
    checks.dereference(parts.get(0), callName(access), state);
    visit(access, state);
    FlowNullCalls.of(node).ifPresent(call -> FlowNullSinks.call(call, state, scope));
  }

  private static String callName(final SyntaxNode access) {
    return access.children().stream()
        .filter(child -> "identifier".equals(child.kind()) && "methodCall".equals(access.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
