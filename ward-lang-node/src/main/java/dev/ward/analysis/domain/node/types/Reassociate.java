package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Repairs the expressions that the grammar reads as a name followed by an expression: {@code f(x)},
 * {@code a + 1}, {@code names[0]} and chains such as {@code load(1).then(g)}. The rule {@code
 * identifierName singleExpression?} makes the parser take the rest of the expression as one operand
 * of the name, so the tree has the wrong shape. The repaired tree has the operators in their
 * precedence order and keeps the span of every node that the grammar produced. An expression that
 * cannot be repaired is left as it is.
 */
final class Reassociate {
  private static final String SINGLE = "singleExpression";
  private static final String ARGUMENTS = "arguments";
  private static final String SEQUENCE = "expressionSequence";
  private static final int INDEX_PARTS = 3;
  private static final Set<String> MEMBER_TOKENS = Set.of(".", "?.", "!", "?");
  private static final Map<String, Integer> PRECEDENCE =
      Map.ofEntries(
          Map.entry("*", 13),
          Map.entry("/", 13),
          Map.entry("%", 13),
          Map.entry("+", 12),
          Map.entry("-", 12),
          Map.entry("<", 10),
          Map.entry(">", 10),
          Map.entry("<=", 10),
          Map.entry(">=", 10),
          Map.entry("instanceof", 10),
          Map.entry("in", 10),
          Map.entry("==", 9),
          Map.entry("!=", 9),
          Map.entry("===", 9),
          Map.entry("!==", 9),
          Map.entry("&", 8),
          Map.entry("^", 7),
          Map.entry("|", 6),
          Map.entry("&&", 5),
          Map.entry("||", 4),
          Map.entry("??", 3));

  /** One step of the operand chain that follows the leading name. */
  private record Item(Kind kind, String op, List<SyntaxNode> nodes) {}

  private enum Kind {
    BINARY,
    CALL,
    MEMBER,
    INDEX,
    NONNULL
  }

  private Reassociate() {}

  /** Returns {@code node} with its misshapen expressions repaired. */
  static SyntaxNode of(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return node;
    }
    final List<SyntaxNode> children = new ArrayList<>();
    boolean changed = false;
    for (final SyntaxNode child : node.children()) {
      final SyntaxNode fixed = of(child);
      changed |= fixed != child;
      children.add(fixed);
    }
    final SyntaxNode current =
        changed ? new SyntaxNode(node.kind(), node.span(), node.fields(), children) : node;
    if (!SINGLE.equals(node.kind())) {
      return current;
    }
    return rotate(repair(current).orElse(current));
  }

  /**
   * The grammar hands the tail of {@code a * b + c} to {@code b}, which leaves a right operand that
   * binds looser than its operator; this turns it into {@code (a * b) + c}.
   */
  private static SyntaxNode rotate(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.size() != INDEX_PARTS || !PRECEDENCE.containsKey(Exprs.token(parts.get(1)))) {
      return node;
    }
    final List<SyntaxNode> right = TreeKit.parts(parts.get(2));
    final boolean looser =
        right.size() == INDEX_PARTS
            && !Exprs.isParenthesized(right)
            && PRECEDENCE.containsKey(Exprs.token(right.get(1)))
            && PRECEDENCE.get(Exprs.token(right.get(1)))
                <= PRECEDENCE.get(Exprs.token(parts.get(1)));
    if (!looser) {
      return node;
    }
    final SyntaxNode left =
        new SyntaxNode(
            SINGLE,
            cover(parts.get(0).span(), right.get(0).span()),
            Map.of(),
            List.of(parts.get(0), parts.get(1), right.get(0)));
    return rotate(
        new SyntaxNode(
            SINGLE, node.span(), Map.of(), List.of(rotate(left), right.get(1), right.get(2))));
  }

  private static Optional<SyntaxNode> repair(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.size() != 2
        || !Exprs.IDENTIFIER_NAME.equals(parts.get(0).kind())
        || !SINGLE.equals(parts.get(1).kind())) {
      return Optional.empty();
    }
    final Optional<List<Item>> items = flatten(parts.get(1));
    if (items.isEmpty()) {
      return Optional.empty();
    }
    final SyntaxNode first = wrap(parts.get(0));
    final SyntaxNode built = build(first, items.get());
    return Optional.of(new SyntaxNode(SINGLE, node.span(), Map.of(), built.children()));
  }

  private static SyntaxNode wrap(final SyntaxNode name) {
    return new SyntaxNode(SINGLE, name.span(), Map.of(), List.of(name));
  }

  private static Optional<List<Item>> flatten(final SyntaxNode rest) {
    final List<SyntaxNode> parts = TreeKit.parts(rest);
    if (parts.isEmpty()) {
      return Optional.empty();
    }
    if (Exprs.isParenthesized(parts)) {
      return Optional.of(
          new ArrayList<>(List.of(new Item(Kind.CALL, "", List.of(arguments(rest))))));
    }
    if (parts.size() == 1 && "arrayLiteral".equals(parts.get(0).kind())) {
      return Optional.of(new ArrayList<>(List.of(new Item(Kind.INDEX, "", index(parts.get(0))))));
    }
    final String first = Exprs.token(parts.get(0));
    if (parts.size() == 2
        && ("+".equals(first) || "-".equals(first))
        && SINGLE.equals(parts.get(1).kind())) {
      return Optional.of(
          new ArrayList<>(List.of(new Item(Kind.BINARY, first, List.of(parts.get(1))))));
    }
    if (SINGLE.equals(parts.get(0).kind())) {
      return spine(parts);
    }
    return Optional.empty();
  }

  private static Optional<List<Item>> spine(final List<SyntaxNode> parts) {
    final Optional<List<Item>> inner = flatten(parts.get(0));
    final Optional<Item> next = suffix(parts.subList(1, parts.size()));
    if (inner.isEmpty() || next.isEmpty()) {
      return Optional.empty();
    }
    inner.get().add(next.get());
    return inner;
  }

  private static Optional<Item> suffix(final List<SyntaxNode> rest) {
    final Optional<Item> postfix = postfixItem(rest);
    return postfix.isPresent() ? postfix : binaryItem(rest);
  }

  private static Optional<Item> postfixItem(final List<SyntaxNode> rest) {
    final String first = Exprs.token(rest.get(0));
    if (rest.size() == 1 && ARGUMENTS.equals(rest.get(0).kind())) {
      return Optional.of(new Item(Kind.CALL, "", List.of(rest.get(0))));
    }
    if (rest.size() == 1 && "!".equals(first)) {
      return Optional.of(new Item(Kind.NONNULL, first, rest));
    }
    if (MEMBER_TOKENS.contains(first)) {
      return Optional.of(new Item(Kind.MEMBER, first, rest));
    }
    return "[".equals(first) && rest.size() == INDEX_PARTS
        ? Optional.of(new Item(Kind.INDEX, "", rest))
        : Optional.empty();
  }

  private static Optional<Item> binaryItem(final List<SyntaxNode> rest) {
    final String first = Exprs.token(rest.get(0));
    return rest.size() == 2 && PRECEDENCE.containsKey(first)
        ? Optional.of(new Item(Kind.BINARY, first, List.of(rest.get(1))))
        : Optional.empty();
  }

  private static SyntaxNode arguments(final SyntaxNode group) {
    return new SyntaxNode(ARGUMENTS, group.span(), Map.of(), group.children());
  }

  private static List<SyntaxNode> index(final SyntaxNode array) {
    final List<SyntaxNode> inner = new ArrayList<>();
    final SyntaxNode list = TreeKit.child(array, "elementList");
    if (list != null) {
      for (final SyntaxNode element : TreeKit.children(list, "arrayElement")) {
        inner.addAll(TreeKit.children(element, SINGLE));
      }
    }
    final List<SyntaxNode> parts = TreeKit.parts(array);
    final SyntaxNode open = parts.get(0);
    final SyntaxNode close = parts.get(parts.size() - 1);
    final Span span =
        inner.isEmpty()
            ? open.span()
            : cover(inner.get(0).span(), inner.get(inner.size() - 1).span());
    return List.of(open, new SyntaxNode(SEQUENCE, span, Map.of(), inner), close);
  }

  private static SyntaxNode build(final SyntaxNode first, final List<Item> items) {
    final Deque<SyntaxNode> operands = new ArrayDeque<>();
    final Deque<Item> operators = new ArrayDeque<>();
    operands.push(first);
    for (final Item item : items) {
      if (item.kind() == Kind.BINARY) {
        while (!operators.isEmpty()
            && PRECEDENCE.get(operators.peek().op()) >= PRECEDENCE.get(item.op())) {
          reduce(operands, operators);
        }
        operators.push(item);
        operands.push(item.nodes().get(0));
      } else {
        operands.push(postfix(operands.pop(), item));
      }
    }
    while (!operators.isEmpty()) {
      reduce(operands, operators);
    }
    return operands.pop();
  }

  private static void reduce(final Deque<SyntaxNode> operands, final Deque<Item> operators) {
    final SyntaxNode right = operands.pop();
    final SyntaxNode left = operands.pop();
    final Item operator = operators.pop();
    final SyntaxNode token = operatorToken(operator, left, right);
    operands.push(
        new SyntaxNode(
            SINGLE, cover(left.span(), right.span()), Map.of(), List.of(left, token, right)));
  }

  private static SyntaxNode operatorToken(
      final Item operator, final SyntaxNode left, final SyntaxNode right) {
    return new SyntaxNode(operator.op(), left.span(), Map.of("text", operator.op()), List.of());
  }

  private static SyntaxNode postfix(final SyntaxNode base, final Item item) {
    final List<SyntaxNode> children = new ArrayList<>();
    children.add(base);
    children.addAll(item.nodes());
    final SyntaxNode last = item.nodes().get(item.nodes().size() - 1);
    return new SyntaxNode(SINGLE, cover(base.span(), last.span()), Map.of(), children);
  }

  private static Span cover(final Span from, final Span to) {
    return new Span(
        from.startLine(),
        from.startColumn(),
        to.endLine(),
        to.endColumn(),
        from.startOffset(),
        to.endOffset());
  }
}
