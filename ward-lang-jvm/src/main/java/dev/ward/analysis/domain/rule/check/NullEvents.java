package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the nullness events of a basic block from its syntax: the writes, dereferences, calls,
 * returns and stores of its statements in evaluation order. It does not enter lambdas, class bodies
 * or the guarded side of {@code &&}, {@code ||} and {@code ?:}, so an event it reports happens
 * whenever the statement runs.
 */
final class NullEvents {

  /**
   * What a branch condition tells about a variable on each of its branches.
   *
   * @param variable the variable tested
   * @param onTrue what it holds when the condition is true, or null
   * @param onFalse what it holds when the condition is false, or null
   */
  record Test(Span variable, Nullity onTrue, Nullity onFalse) {}

  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String NEW = "new";
  private static final String NULL = "null";
  private static final String EQUALS = "==";
  private static final int BINARY = 3;
  private static final int INDEX = 4;
  private static final int TERNARY = 5;
  private static final Pattern NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final Pattern NULL_LITERAL =
      Pattern.compile("(?:\\([A-Za-z0-9_.<>\\[\\]]*\\))*\\(*null\\)*");
  private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
  private static final Set<String> SKIPPED =
      Set.of("lambdaExpression", "classBody", "switchExpression");
  private static final Set<String> NESTED =
      Set.of("if", "for", "while", "do", "try", "switch", "synchronized", "assert", "yield");
  private static final Set<String> NON_NULL_WORDS = Set.of("this", "true", "false");

  private final Map<Span, Span> locals = new HashMap<>();
  private final List<NullEvent> out = new ArrayList<>();

  private NullEvents(final BasicBlock block) {
    for (final VariableAccess access : block.accesses()) {
      if (access.kind() != AccessKind.CAPTURE) {
        locals.put(access.span(), access.declaration());
      }
    }
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
    final NullEvents events = new NullEvents(block);
    final SyntaxNode condition = block.nodes().get(block.nodes().size() - 1);
    return events.nullTest(condition).or(() -> events.instanceTest(condition));
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
      out.add(NullEvent.returned(parts.get(1), operand(parts.get(1))));
    } else if ("throw".equals(keyword) && parts.size() > 1) {
      visit(parts.get(1));
    } else if (!NESTED.contains(keyword)) {
      parts.forEach(this::visit);
    }
  }

  private void declarator(final SyntaxNode node) {
    final SyntaxNode id =
        node.children().stream()
            .filter(part -> "variableDeclaratorId".equals(part.kind()))
            .findFirst()
            .orElse(null);
    final Span variable = id == null ? null : locals.get(id.span());
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
      out.add(NullEvent.write(variable, init.get(), operand(init.get())));
    } else {
      out.add(NullEvent.kill(variable));
    }
  }

  private void expression(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final String operator = parts.size() == BINARY ? SyntaxWalk.text(parts.get(1)) : "";
    if (Assignments.isAssignment(node)) {
      assignment(node);
    } else if ("&&".equals(operator) || "||".equals(operator)) {
      visit(parts.get(0));
    } else if (parts.size() == TERNARY && "?".equals(SyntaxWalk.text(parts.get(1)))) {
      visit(parts.get(0));
    } else if (nullTest(node).isPresent()) {
      out.add(NullEvent.compare(nullTest(node).get().variable(), node));
    } else if (".".equals(operator)) {
      member(node);
    } else if (parts.size() == INDEX && "[".equals(SyntaxWalk.text(parts.get(1)))) {
      visit(parts.get(0));
      visit(parts.get(2));
      dereference(parts.get(0), "");
    } else {
      other(node);
    }
  }

  private void other(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == 1 && METHOD_CALL.equals(parts.get(0).kind())) {
      call(node, parts.get(0));
    } else if (parts.size() == 2 && NEW.equals(SyntaxWalk.text(parts.get(0)))) {
      final List<SyntaxNode> args = NullFlow.arguments(parts.get(1));
      args.forEach(this::visit);
      out.add(NullEvent.call(node, arguments(args), "<init>"));
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
      final String name = methodName(tail);
      dereference(parts.get(0), "close".equals(name) && args.isEmpty() ? name : "");
      out.add(NullEvent.call(node, arguments(args), name));
    } else {
      dereference(parts.get(0), "");
    }
  }

  private void call(final SyntaxNode node, final SyntaxNode invoked) {
    final List<SyntaxNode> args = NullFlow.arguments(invoked);
    args.forEach(this::visit);
    out.add(NullEvent.call(node, arguments(args), methodName(invoked)));
  }

  private void assignment(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode target = parts.get(0);
    final SyntaxNode value = parts.get(2);
    final boolean plain = "=".equals(SyntaxWalk.text(parts.get(1)));
    visit(value);
    final Span local = locals.get(target.span());
    final String field = fieldName(target);
    if (local != null) {
      out.add(plain ? NullEvent.write(local, value, operand(value)) : NullEvent.kill(local));
    } else if (plain && field != null) {
      out.add(NullEvent.store(node, operand(value), field));
    } else {
      visit(target);
    }
  }

  private static String fieldName(final SyntaxNode target) {
    final String text = SyntaxWalk.text(target);
    final String bare = text.startsWith("this.") ? text.substring("this.".length()) : text;
    return NAME.matcher(bare).matches() ? bare : null;
  }

  private void dereference(final SyntaxNode receiver, final String method) {
    final Span variable = variableOf(receiver);
    if (variable != null) {
      out.add(NullEvent.deref(variable, receiver, method));
    }
  }

  private List<NullEvent.Arg> arguments(final List<SyntaxNode> args) {
    return args.stream().map(arg -> new NullEvent.Arg(operand(arg), arg)).toList();
  }

  private static String methodName(final SyntaxNode invoked) {
    return NullSyntax.first(invoked, "identifier").map(SyntaxWalk::text).orElse("");
  }

  private Span variableOf(final SyntaxNode node) {
    return EXPRESSION.equals(node.kind()) && NAME.matcher(SyntaxWalk.text(node)).matches()
        ? locals.get(node.span())
        : null;
  }

  private NullEvent.Operand operand(final SyntaxNode node) {
    final String text = SyntaxWalk.text(node);
    final NullEvent.Operand operand;
    if (NULL_LITERAL.matcher(text).matches()) {
      operand = new NullEvent.Operand(null, true, false);
    } else if (variableOf(node) != null) {
      operand = new NullEvent.Operand(variableOf(node), false, false);
    } else if (neverNull(node, text)) {
      operand = new NullEvent.Operand(null, false, true);
    } else {
      operand = NullEvent.Operand.UNKNOWN;
    }
    return operand;
  }

  private static boolean neverNull(final SyntaxNode node, final String text) {
    final List<SyntaxNode> parts = node.children();
    return NON_NULL_WORDS.contains(text)
        || STRING.matcher(text).matches()
        || parts.size() == 2 && NEW.equals(SyntaxWalk.text(parts.get(0)));
  }

  /** Reads {@code variable ==|!= null}: the variable and what each branch holds. */
  private Optional<Test> nullTest(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final String operator = NullSyntax.operator(node);
    if (!EQUALS.equals(operator) && !"!=".equals(operator)) {
      return Optional.empty();
    }
    final boolean nullLeft = NULL.equals(SyntaxWalk.text(parts.get(0)));
    final Span variable = variableOf(parts.get(nullLeft ? 2 : 0));
    if (variable == null || !nullLeft && !NULL.equals(SyntaxWalk.text(parts.get(2)))) {
      return Optional.empty();
    }
    final boolean equal = EQUALS.equals(operator);
    return Optional.of(
        new Test(
            variable,
            equal ? Nullity.NULL : Nullity.NONNULL,
            equal ? Nullity.NONNULL : Nullity.NULL));
  }

  private Optional<Test> instanceTest(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (!EXPRESSION.equals(node.kind())
        || parts.size() < BINARY
        || !"instanceof".equals(SyntaxWalk.text(parts.get(1)))) {
      return Optional.empty();
    }
    return Optional.ofNullable(variableOf(parts.get(0)))
        .map(variable -> new Test(variable, Nullity.NONNULL, null));
  }
}
