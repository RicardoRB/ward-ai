package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a callback of an array method that does not return a value, like ESLint {@code
 * array-callback-return}. A callback whose body end is reachable is reported at its arrow or
 * function, and a {@code return} without a value is reported unless {@code allowImplicit} is set.
 * {@code forEach} is checked only with {@code checkForEach}; then a returned value is reported.
 */
public final class NodeArrayCallbackReturnCheck implements RuleCheck {

  private static final Pattern TARGET_METHODS =
      Pattern.compile(
          "every|filter|find(Last)?(Index)?|flatMap|forEach|map|reduce(Right)?|some|sort|toSorted");
  private static final String FOR_EACH = "forEach";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT = "argument";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String SINGLE = "singleExpression";
  private static final String FUNCTION = "anonymousFunction";
  private static final String ARROW = "arrowFunctionDeclaration";
  private static final String EXPRESSION_SEQUENCE = "expressionSequence";
  private static final String DOT = ".";
  private static final int MEMBER_PARTS = 3;
  private static final Set<String> NESTED = NodeKinds.NESTED_BODIES;

  private final Options options;
  private final String message;

  /** The options of the check, as ESLint names them. */
  public record Options(boolean allowImplicit, boolean checkForEach, boolean allowVoid) {}

  /**
   * Creates the check.
   *
   * @param options the ESLint options
   * @param message the message of each finding
   */
  public NodeArrayCallbackReturnCheck(final Options options, final String message) {
    this.options = Objects.requireNonNull(options, "options");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return matches(root, FlowView.NONE);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, flow, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final FlowView flow, final List<RuleMatch> matches) {
    final Optional<String> method = targetMethod(node);
    if (method.isPresent()) {
      firstArgumentFunction(node)
          .ifPresent(function -> checkCallback(function, method.get(), flow, matches));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, flow, matches);
    }
  }

  /** The array method name of a member call, when the name is one of the target methods. */
  private static Optional<String> targetMethod(final SyntaxNode call) {
    final List<SyntaxNode> children = call.children();
    if (children.size() != 2
        || !ARGUMENTS.equals(children.get(1).kind())
        || !SINGLE.equals(children.get(0).kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> member = children.get(0).children();
    if (member.size() != MEMBER_PARTS || !DOT.equals(LeafText.of(member.get(1)))) {
      return Optional.empty();
    }
    final String name = LeafText.of(member.get(2));
    return TARGET_METHODS.matcher(name).matches() ? Optional.of(name) : Optional.empty();
  }

  /** The function that is the first argument of a call, when it is one. */
  private static Optional<SyntaxNode> firstArgumentFunction(final SyntaxNode call) {
    final List<SyntaxNode> items = new ArrayList<>();
    for (final SyntaxNode child : call.children().get(1).children()) {
      items.addAll(ARGUMENT_LIST.equals(child.kind()) ? child.children() : List.of(child));
    }
    return items.stream()
        .filter(item -> ARGUMENT.equals(item.kind()))
        .findFirst()
        .flatMap(
            argument ->
                argument.children().stream()
                    .filter(c -> SINGLE.equals(c.kind()))
                    .findFirst()
                    .flatMap(NodeArrayCallbackReturnCheck::functionOf));
  }

  /** The arrow or function of an expression; a function expression may wrap an arrow. */
  private static Optional<SyntaxNode> functionOf(final SyntaxNode expression) {
    for (final SyntaxNode child : expression.children()) {
      if (ARROW.equals(child.kind())) {
        return Optional.of(child);
      }
      if (FUNCTION.equals(child.kind())) {
        final Optional<SyntaxNode> arrow =
            child.children().stream().filter(c -> ARROW.equals(c.kind())).findFirst();
        return arrow.isPresent() ? arrow : Optional.of(child);
      }
    }
    return Optional.empty();
  }

  private void checkCallback(
      final SyntaxNode function,
      final String method,
      final FlowView flow,
      final List<RuleMatch> matches) {
    final boolean forEach = FOR_EACH.equals(method);
    if (forEach && !options.checkForEach()) {
      return;
    }
    if (Arrows.isExpressionBody(function)) {
      checkExpressionBody(function, forEach, matches);
      return;
    }
    checkBlockBody(function, forEach, flow, matches);
  }

  /** An arrow with an expression body returns its value; forEach must not, unless it is void. */
  private void checkExpressionBody(
      final SyntaxNode function, final boolean forEach, final List<RuleMatch> matches) {
    if (forEach && !Voids.voidedArrow(function, options.allowVoid())) {
      matches.add(new RuleMatch(Arrows.reportSpan(function), message));
    }
  }

  private void checkBlockBody(
      final SyntaxNode function,
      final boolean forEach,
      final FlowView flow,
      final List<RuleMatch> matches) {
    final List<SyntaxNode> returns = new ArrayList<>();
    Bodies.returnsIn(function.children(), returns);
    if (!forEach && Bodies.endReachable(function, flow, returns)) {
      matches.add(new RuleMatch(Arrows.reportSpan(function), message));
    }
    for (final SyntaxNode statement : returns) {
      if (reportsReturn(statement, forEach)) {
        matches.add(new RuleMatch(statement.span(), message));
      }
    }
  }

  /** A return is reported without a value (not forEach), or with one (forEach, when checked). */
  private boolean reportsReturn(final SyntaxNode statement, final boolean forEach) {
    final boolean valued =
        statement.children().stream().anyMatch(c -> EXPRESSION_SEQUENCE.equals(c.kind()));
    if (forEach) {
      return valued && !(options.allowVoid() && Voids.voidedReturn(statement));
    }
    return !options.allowImplicit() && !valued;
  }

  /** The reachability of the end of a body, and the returns in it. */
  private static final class Bodies {

    private static final String RETURN = "returnStatement";

    static boolean endReachable(
        final SyntaxNode function, final FlowView flow, final List<SyntaxNode> returns) {
      final Optional<ControlFlowGraph> graph = NodeFlow.graphOf(flow, function);
      return graph.map(NodeFlow::fallsOffEnd).orElseGet(returns::isEmpty);
    }

    static void returnsIn(final List<SyntaxNode> nodes, final List<SyntaxNode> returns) {
      for (final SyntaxNode node : nodes) {
        if (RETURN.equals(node.kind())) {
          returns.add(node);
        }
        if (!NESTED.contains(node.kind())) {
          returnsIn(node.children(), returns);
        }
      }
    }
  }

  /** The parts of an arrow function: its arrow token, its body and where ESLint reports it. */
  private static final class Arrows {

    private static final String ARROW_TOKEN = "=>";
    private static final String FUNCTION_BODY = "functionBody";

    /** Whether the function is an arrow with an expression body. */
    static boolean isExpressionBody(final SyntaxNode function) {
      return arrowToken(function).isPresent() && !hasBlockBody(function);
    }

    /** An arrow is reported at its arrow token, and a function expression at its start. */
    static Span reportSpan(final SyntaxNode function) {
      return arrowToken(function).map(SyntaxNode::span).orElse(function.span());
    }

    private static Optional<SyntaxNode> arrowToken(final SyntaxNode function) {
      return function.children().stream()
          .filter(c -> c.children().isEmpty() && ARROW_TOKEN.equals(LeafText.of(c)))
          .findFirst();
    }

    private static boolean hasBlockBody(final SyntaxNode function) {
      return function.children().stream()
          .anyMatch(
              c ->
                  FUNCTION_BODY.equals(c.kind())
                      || c.children().stream().anyMatch(d -> FUNCTION_BODY.equals(d.kind())));
    }
  }

  /** The {@code void} operator at the start of an expression, which an option may allow. */
  private static final class Voids {

    private static final String VOID = "void";
    private static final String ARROW_BODY = "arrowFunctionBody";

    /** Whether an arrow's expression body starts with {@code void} and the option allows it. */
    static boolean voidedArrow(final SyntaxNode function, final boolean allowVoid) {
      return allowVoid
          && function.children().stream()
              .filter(c -> SINGLE.equals(c.kind()) || ARROW_BODY.equals(c.kind()))
              .anyMatch(Voids::startsWithVoid);
    }

    /** Whether a return's value starts with {@code void}. */
    static boolean voidedReturn(final SyntaxNode statement) {
      return statement.children().stream()
          .filter(c -> EXPRESSION_SEQUENCE.equals(c.kind()))
          .anyMatch(Voids::startsWithVoid);
    }

    /**
     * Whether an expression starts with the {@code void} operator, reading down its first parts.
     */
    private static boolean startsWithVoid(final SyntaxNode node) {
      if (node.children().isEmpty()) {
        return VOID.equals(LeafText.of(node));
      }
      return startsWithVoid(node.children().get(0));
    }
  }
}
