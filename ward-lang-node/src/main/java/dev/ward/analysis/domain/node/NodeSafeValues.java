package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether the value of an expression can carry text that an attacker chose. A value is
 * safe when the program built it from constants, from arithmetic and comparisons (their results
 * are numbers and booleans), from the numeric conversions and members, and from names whose every
 * reaching assignment is safe. A parameter, a call, a member of an object or any other expression
 * is not safe.
 *
 * <p>In {@link Mode#LOCAL_HOST} the values that are safe besides constants are instead the {@code
 * address} of the socket that a server reports for itself, {@code server.address().address}: a
 * request to it stays on the machine.
 *
 * <p>shortcut: an assignment is read from a declaration or an assignment expression of the
 * statement of the write, and a name written by a loop binding or a destructuring is not safe;
 * upgrade when a project builds a value through a destructured declaration.
 */
public final class NodeSafeValues {
  /** Which values the analysis accepts besides constants. */
  public enum Mode {
    /** Numbers and booleans, as the value of markup. */
    MARKUP,
    /** The address of a socket of the program, as the host of a URL. */
    LOCAL_HOST
  }

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
  private static final Pattern ADDRESS_CALL = Pattern.compile(".*\\.address\\(\\)");
  private static final Set<String> JOINING = Set.of("&&", "||", "??", "+");
  private static final int BINARY_PARTS = 3;
  private static final int UNARY_PARTS = 2;
  private static final int TERNARY_PARTS = 5;
  private static final int OPERATOR_INDEX = 1;
  private static final int RIGHT_INDEX = 2;
  private static final int ELSE_INDEX = 4;
  private static final int MAX_DEPTH = 12;
  private static final String CAST = "as";
  private static final String MEMBER = ".";
  private static final String NOT_NULL = "!";
  private static final String AWAIT = "await";
  private static final String ADDRESS = "address";

  private final NodeValueFlow flow;
  private final Mode mode;

  /**
   * Creates the analysis over the graphs of a file.
   *
   * @param graphs the control-flow graphs of the file
   * @param mode which values are safe besides constants
   */
  public NodeSafeValues(final List<ControlFlowGraph> graphs, final Mode mode) {
    this.flow = new NodeValueFlow(graphs);
    this.mode = mode;
  }

  /**
   * Whether the value of an expression is safe.
   *
   * @param value the expression
   * @return true when no attacker-chosen text can be in its value
   */
  public boolean isSafe(final SyntaxNode value) {
    return safe(value, new HashSet<>(), 0);
  }

  /**
   * Whether the host of a URL is the address of a socket of the program.
   *
   * @param url the first argument of a request, a string or a template
   * @return true when the host part is built from an address of the program
   */
  public boolean isLocalUrl(final SyntaxNode url) {
    return new NodeUrlHosts(this::isSafe).isLocal(url);
  }

  private boolean safe(final SyntaxNode node, final Set<AccessSite> visiting, final int depth) {
    final SyntaxNode value = NodeFlowSyntax.unwrap(node);
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(value);
    if (depth > MAX_DEPTH || !NodeTemplates.SINGLE.equals(value.kind())) {
      return false;
    }
    return switch (parts.size()) {
      case 1 -> single(parts.get(0), value, visiting, depth);
      case UNARY_PARTS -> twoParts(parts, visiting, depth);
      case BINARY_PARTS -> binary(parts, visiting, depth);
      case TERNARY_PARTS ->
          safe(parts.get(RIGHT_INDEX), visiting, depth + 1)
              && safe(parts.get(ELSE_INDEX), visiting, depth + 1);
      default -> false;
    };
  }

  private boolean single(
      final SyntaxNode part,
      final SyntaxNode value,
      final Set<AccessSite> visiting,
      final int depth
  ) {
    if (NodeTemplates.isLiteral(part)) {
      return NodeTemplates.of(value).map(t -> templateSafe(t, visiting, depth)).orElse(true);
    }
    return IDENTIFIER.matcher(NodeFlowSyntax.text(value)).matches()
        && flow.allAssigned(value, visiting, depth, this::safe, mode == Mode.MARKUP);
  }

  private boolean templateSafe(
      final SyntaxNode template,
      final Set<AccessSite> visiting,
      final int depth
  ) {
    return NodeFlowSyntax.parts(template).stream()
        .map(NodeTemplates::expression)
        .flatMap(Optional::stream)
        .allMatch(expression -> safe(expression, visiting, depth + 1));
  }

  private boolean twoParts(
      final List<SyntaxNode> parts,
      final Set<AccessSite> visiting,
      final int depth
  ) {
    if (NOT_NULL.equals(NodeFlowSyntax.text(parts.get(1)))) {
      return safe(parts.get(0), visiting, depth + 1);
    }
    if (AWAIT.equals(NodeFlowSyntax.text(parts.get(0)))) {
      return safe(parts.get(1), visiting, depth + 1);
    }
    return mode == Mode.MARKUP && NodeNumericShapes.unaryOrCall(parts);
  }

  private boolean binary(
      final List<SyntaxNode> parts,
      final Set<AccessSite> visiting,
      final int depth
  ) {
    final String operator = NodeFlowSyntax.text(parts.get(OPERATOR_INDEX));
    if (mode == Mode.MARKUP && NodeNumericShapes.operator(operator)) {
      return true;
    }
    if (JOINING.contains(operator)) {
      return safe(parts.get(0), visiting, depth + 1)
          && safe(parts.get(RIGHT_INDEX), visiting, depth + 1);
    }
    if (CAST.equals(operator)) {
      return safe(parts.get(0), visiting, depth + 1);
    }
    if (!MEMBER.equals(operator)) {
      return false;
    }
    final String name = NodeFlowSyntax.text(parts.get(RIGHT_INDEX));
    return mode == Mode.MARKUP
        ? NodeNumericShapes.member(name)
        : ADDRESS.equals(name) && origin(parts.get(0), visiting, depth + 1);
  }

  /** Whether an expression is the address object of a socket: {@code server.address()}. */
  private boolean origin(final SyntaxNode node, final Set<AccessSite> visiting, final int depth) {
    final SyntaxNode value = NodeFlowSyntax.unwrap(node);
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(value);
    final String text = NodeFlowSyntax.text(value);
    if (depth > MAX_DEPTH || !NodeTemplates.SINGLE.equals(value.kind())) {
      return false;
    }
    if (parts.size() == BINARY_PARTS
        && CAST.equals(NodeFlowSyntax.text(parts.get(OPERATOR_INDEX)))) {
      return origin(parts.get(0), visiting, depth + 1);
    }
    if (parts.size() == UNARY_PARTS) {
      return ADDRESS_CALL.matcher(text).matches();
    }
    return IDENTIFIER.matcher(text).matches()
        && flow.allAssigned(value, visiting, depth, this::origin, false);
  }
}
