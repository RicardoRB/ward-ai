package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the nullness events of a basic block from its syntax: the writes, dereferences, calls,
 * returns and stores of its statements in evaluation order. It does not enter lambdas, class bodies
 * or the guarded side of {@code &&}, {@code ||} and {@code ?:}, so an event it reports happens
 * whenever the statement runs.
 */
final class NullEvents {

  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String NEW = "new";
  private static final String DOT = ".";
  private static final int BINARY = 3;
  private static final int INDEX = 4;
  private static final int CREATION = 2;
  private static final Set<String> SKIPPED =
      Set.of("lambdaExpression", "classBody", "switchExpression");
  private static final Set<String> NESTED =
      Set.of("if", "for", "while", "do", "try", "switch", "synchronized", "assert", "yield");

  private final NullOperands operands;
  private final List<NullEvent> out = new ArrayList<>();

  /**
   * What a branch condition tells about a variable on each of its branches.
   *
   * @param variable the variable tested
   * @param onTrue what it holds when the condition is true, or null
   * @param onFalse what it holds when the condition is false, or null
   */
  record Test(Span variable, Nullity onTrue, Nullity onFalse) {}

  private NullEvents(final BasicBlock block) {
    operands = new NullOperands(block);
    block.accesses().stream()
        .filter(access -> access.kind() == AccessKind.CAPTURE)
        .map(access -> NullEvent.kill(access.declaration()))
        .forEach(out::add);
  }

  /** Returns the events of the nodes of {@code block}, in evaluation order. */
  static List<NullEvent> of(final BasicBlock block) {
    final NullEvents events = new NullEvents(block);
    block.nodes().forEach(events::visit);
    return List.copyOf(events.out);
  }

  /** Returns what the condition that ends {@code block} says about a variable, if it does. */
  static Optional<Test> test(final BasicBlock block) {
    final boolean branches =
        block.successors().stream()
            .map(FlowEdge::kind)
            .anyMatch(kind -> kind == FlowEdgeKind.TRUE || kind == FlowEdgeKind.FALSE);
    if (!branches || block.nodes().isEmpty()) {
      return Optional.empty();
    }
    final NullOperands reader = new NullOperands(block);
    final SyntaxNode condition = block.nodes().get(block.nodes().size() - 1);
    return reader.nullTest(condition).or(() -> reader.instanceTest(condition));
  }

  private void visit(final SyntaxNode node) {
    if (SKIPPED.contains(node.kind())) {
      return;
    }
    switch (node.kind()) {
      case "variableDeclarator" -> declarator(node);
      case EXPRESSION -> expression(node);
      case "statement" -> statement(node);
      default -> node.children().forEach(this::visit);
    }
  }

  private void statement(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final String keyword = parts.isEmpty() ? "" : SyntaxWalk.text(parts.get(0));
    if ("return".equals(keyword) && parts.size() > 1 && EXPRESSION.equals(parts.get(1).kind())) {
      visit(parts.get(1));
      out.add(NullEvent.returned(parts.get(1), operands.operand(parts.get(1))));
    } else if ("throw".equals(keyword) && parts.size() > 1) {
      visit(parts.get(1));
    } else if (!NESTED.contains(keyword)) {
      parts.forEach(this::visit);
    }
  }

  private void declarator(final SyntaxNode node) {
    final Span variable =
        node.children().stream()
            .filter(part -> "variableDeclaratorId".equals(part.kind()))
            .findFirst()
            .map(id -> operands.local(id.span()))
            .orElse(null);
    if (variable == null) {
      return;
    }
    final Optional<SyntaxNode> init =
        node.children().stream()
            .filter(part -> "variableInitializer".equals(part.kind()))
            .flatMap(part -> part.children().stream())
            .filter(part -> EXPRESSION.equals(part.kind()))
            .findFirst();
    if (init.isPresent()) {
      visit(init.get());
      out.add(NullEvent.write(variable, init.get(), operands.operand(init.get())));
    } else {
      out.add(NullEvent.kill(variable));
    }
  }

  private void expression(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final String operator = parts.size() == BINARY ? SyntaxWalk.text(parts.get(1)) : "";
    final Optional<Test> compared = operands.nullTest(node);
    if (Assignments.isAssignment(node)) {
      assignment(node);
    } else if (NullOperands.isGuarded(operator, parts)) {
      visit(parts.get(0));
    } else if (compared.isPresent()) {
      out.add(NullEvent.compare(compared.get().variable(), node));
    } else if (DOT.equals(operator)) {
      member(node);
    } else if ("==".equals(operator) || "!=".equals(operator)) {
      parts.forEach(this::visit);
      out.add(NullEvent.equality(node, argument(parts.get(0)), argument(parts.get(2))));
    } else {
      other(node);
    }
  }

  private NullEvent.Arg argument(final SyntaxNode node) {
    return new NullEvent.Arg(operands.operand(node), node);
  }

  private void other(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == INDEX && "[".equals(SyntaxWalk.text(parts.get(1)))) {
      visit(parts.get(0));
      visit(parts.get(2));
      operands.dereference(parts.get(0), "").ifPresent(out::add);
    } else if (parts.size() == 1 && METHOD_CALL.equals(parts.get(0).kind())) {
      final List<SyntaxNode> args = NullFlow.arguments(parts.get(0));
      args.forEach(this::visit);
      out.add(
          NullEvent.call(node, operands.arguments(args), NullOperands.methodName(parts.get(0))));
    } else if (parts.size() == CREATION && NEW.equals(SyntaxWalk.text(parts.get(0)))) {
      final List<SyntaxNode> args = NullFlow.arguments(parts.get(1));
      args.forEach(this::visit);
      out.add(NullEvent.call(node, operands.arguments(args), "<init>"));
    } else {
      parts.forEach(this::visit);
    }
  }

  private void member(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    visit(parts.get(0));
    final SyntaxNode tail = parts.get(2);
    if (METHOD_CALL.equals(tail.kind())) {
      final List<SyntaxNode> args = NullFlow.arguments(tail);
      args.forEach(this::visit);
      final String name = NullOperands.methodName(tail);
      operands
          .dereference(parts.get(0), "close".equals(name) && args.isEmpty() ? name : "")
          .ifPresent(out::add);
      out.add(NullEvent.call(node, operands.arguments(args), name));
      if ("equals".equals(name) && args.size() == 1) {
        out.add(NullEvent.equality(node, argument(parts.get(0)), argument(args.get(0))));
      }
    } else {
      operands.dereference(parts.get(0), "").ifPresent(out::add);
    }
  }

  private void assignment(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode target = parts.get(0);
    final SyntaxNode value = parts.get(2);
    final boolean plain = "=".equals(SyntaxWalk.text(parts.get(1)));
    visit(value);
    final Span local = operands.local(target.span());
    final String field = NullOperands.fieldName(target);
    if (local != null) {
      out.add(
          plain ? NullEvent.write(local, value, operands.operand(value)) : NullEvent.kill(local));
    } else if (plain && field != null) {
      out.add(NullEvent.store(node, operands.operand(value), field));
    } else {
      visit(target);
    }
  }
}
