package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the flow sites of one TypeScript or JavaScript body: calls and member reads, with the
 * receiver type of their qualifier. Inner sites come before the sites that contain them, and a
 * nested function or class is another body, so it is not entered.
 */
final class NodeFlowSites implements FlowSites {

  private static final String SINGLE = "singleExpression";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT = "argument";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String VARIABLE_DECLARATION = "variableDeclaration";
  private static final String NEW = "new";
  private static final String EXPRESSION_SEQUENCE = "expressionSequence";
  private static final String CONSTRUCTOR = "<init>";
  private static final Set<String> MEMBER_TOKENS = Set.of(".", "?.");
  private static final Set<String> ASSIGNMENTS = Set.of("=", "+=");
  private static final int MEMBER_PARTS = 3;
  private static final int ASSIGNMENT_PARTS = 3;
  private static final int NEW_PARTS = 3;
  private static final int KEY_PARTS = 4;
  private static final Pattern STRING_KEY = Pattern.compile("(['\"])([\\w$]+)\\1");

  private final NodeTypes types;
  private final Predicate<SyntaxNode> skipped;

  /**
   * Creates the finder of one body.
   *
   * @param types the declared types of the body
   * @param skipped reports the nodes that the lowering added as blocks of their own
   */
  NodeFlowSites(final NodeTypes types, final Predicate<SyntaxNode> skipped) {
    this.types = types;
    this.skipped = skipped;
  }

  @Override
  public List<FlowSite> of(final SyntaxNode node) {
    final List<FlowSite> found = new ArrayList<>();
    walk(node, found);
    return found;
  }

  private void walk(final SyntaxNode node, final List<FlowSite> found) {
    if (skipped.test(node)
        || NodeAccesses.CAPTURERS.contains(node.kind())
        || NodeSyntax.SKIPPED.contains(node.kind())) {
      return;
    }
    if (SINGLE.equals(node.kind())) {
      expression(node, found);
    } else if (NodeStatementCalls.isExpressionStatement(node)) {
      statement(node, found);
    } else {
      children(node, found);
      if (VARIABLE_DECLARATION.equals(node.kind())) {
        types.declareVariable(node);
      }
    }
  }

  /**
   * A statement the grammar parses as a declaration may be a call, or a bracket write such as
   * {@code a['b'] = v}, which is recorded as a member write after the sites in its value.
   */
  private void statement(final SyntaxNode node, final List<FlowSite> found) {
    final Optional<NodeStatementCalls.Call> call = NodeStatementCalls.callOf(node);
    if (call.isPresent()) {
      call(call.get().callee(), call.get().arguments(), found);
      return;
    }
    children(node, found);
    NodeStatementCalls.bracketWriteOf(node)
        .ifPresent(
            write -> {
              final String qualifier = write.qualifier();
              found.add(
                  new FlowSite(
                      FlowSiteKind.MEMBER_WRITE,
                      write.key(),
                      qualifier,
                      types.receiverType(qualifier),
                      List.of(),
                      spans(List.of(write.value())),
                      node.span()));
            });
  }

  private void children(final SyntaxNode node, final List<FlowSite> found) {
    for (final SyntaxNode child : node.children()) {
      walk(child, found);
    }
  }

  /** A call is an expression of a callee and an arguments node; a dotted name is a member read. */
  private void expression(final SyntaxNode node, final List<FlowSite> found) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (parts.size() == 2 && ARGUMENTS.equals(parts.get(1).kind())) {
      call(parts.get(0), argumentValues(parts.get(1)), found);
    } else if (parts.size() == 2 && isParenthesized(parts.get(1))) {
      call(parts.get(0), parenthesizedValues(parts.get(1)), found);
    } else if (isMember(parts)) {
      walk(parts.get(0), found);
      found.add(siteOf(FlowSiteKind.MEMBER_READ, node, parts, List.of()));
    } else if (isNew(parts)) {
      children(node, found);
      found.add(constructor(node, parts));
    } else {
      children(node, found);
      writeOf(node, parts).ifPresent(found::add);
    }
  }

  /** A {@code new X(args)} expression is a call named {@code <init>} on {@code X}. */
  private FlowSite constructor(final SyntaxNode node, final List<SyntaxNode> parts) {
    final String qualifier = NodeFlowSyntax.text(parts.get(1));
    return new FlowSite(
        FlowSiteKind.CALL,
        CONSTRUCTOR,
        qualifier,
        types.receiverType(qualifier),
        List.of(),
        spans(argumentValues(parts.get(2))),
        node.span());
  }

  /**
   * An assignment to a property, {@code a.b = v} or {@code a['b'] = v}, is a member write whose
   * argument 0 is the value. The site is listed after the sites inside the value.
   */
  private Optional<FlowSite> writeOf(final SyntaxNode node, final List<SyntaxNode> parts) {
    if (parts.size() != ASSIGNMENT_PARTS
        || !ASSIGNMENTS.contains(NodeFlowSyntax.text(parts.get(1)))) {
      return Optional.empty();
    }
    final List<SyntaxNode> target = NodeFlowSyntax.parts(parts.get(0));
    return writtenName(target)
        .map(
            name -> {
              final String qualifier = NodeFlowSyntax.text(target.get(0));
              return new FlowSite(
                  FlowSiteKind.MEMBER_WRITE,
                  name,
                  qualifier,
                  types.receiverType(qualifier),
                  List.of(),
                  spans(List.of(parts.get(2))),
                  node.span());
            });
  }

  /**
   * The property of an assignment target: the name of {@code a.b}, or the key of {@code a['b']}.
   */
  private static Optional<String> writtenName(final List<SyntaxNode> target) {
    if (isMember(target)) {
      return Optional.of(NodeFlowSyntax.text(target.get(target.size() - 1)));
    }
    if (target.size() != KEY_PARTS
        || !"[".equals(NodeFlowSyntax.text(target.get(1)))
        || !"]".equals(NodeFlowSyntax.text(target.get(3)))) {
      return Optional.empty();
    }
    final Matcher key = STRING_KEY.matcher(NodeFlowSyntax.text(target.get(2)));
    return key.matches() ? Optional.of(key.group(2)) : Optional.empty();
  }

  /**
   * The grammar gives a call in an initializer or an assignment value a parenthesized expression as
   * its second part, not an arguments node.
   */
  private static boolean isParenthesized(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    return parts.size() >= 2
        && "(".equals(NodeFlowSyntax.text(parts.get(0)))
        && ")".equals(NodeFlowSyntax.text(parts.get(parts.size() - 1)));
  }

  private static List<SyntaxNode> parenthesizedValues(final SyntaxNode node) {
    final SyntaxNode sequence =
        NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(node), EXPRESSION_SEQUENCE);
    if (sequence == null) {
      return List.of();
    }
    return sequence.children().stream().filter(child -> !child.children().isEmpty()).toList();
  }

  private static boolean isNew(final List<SyntaxNode> parts) {
    return parts.size() == NEW_PARTS
        && NEW.equals(NodeFlowSyntax.text(parts.get(0)))
        && ARGUMENTS.equals(parts.get(2).kind());
  }

  /** The callee's receiver and the arguments come first, so inner sites are listed before. */
  private void call(
      final SyntaxNode callee, final List<SyntaxNode> values, final List<FlowSite> found) {
    final List<SyntaxNode> calleeParts = NodeFlowSyntax.parts(callee);
    if (isMember(calleeParts)) {
      walk(calleeParts.get(0), found);
    }
    values.forEach(value -> walk(value, found));
    if (isMember(calleeParts)) {
      found.add(siteOf(FlowSiteKind.CALL, callee, calleeParts, values));
    } else {
      final String written = NodeFlowSyntax.text(callee);
      found.add(
          new FlowSite(
              FlowSiteKind.CALL,
              types.calleeName(written),
              "",
              types.calleeReceiver(written),
              List.of(),
              spans(values),
              callee.span()));
    }
  }

  private FlowSite siteOf(
      final FlowSiteKind kind,
      final SyntaxNode node,
      final List<SyntaxNode> parts,
      final List<SyntaxNode> values) {
    final String qualifier = NodeFlowSyntax.text(parts.get(0));
    return new FlowSite(
        kind,
        NodeFlowSyntax.text(parts.get(parts.size() - 1)),
        qualifier,
        types.receiverType(qualifier),
        List.of(),
        spans(values),
        node.span());
  }

  /** A dotted name ends in a member name, with a member token before it. */
  private static boolean isMember(final List<SyntaxNode> parts) {
    if (parts.size() < MEMBER_PARTS) {
      return false;
    }
    final StringBuilder tokens = new StringBuilder();
    for (final SyntaxNode part : parts.subList(1, parts.size() - 1)) {
      tokens.append(NodeFlowSyntax.text(part));
    }
    return MEMBER_TOKENS.contains(tokens.toString())
        && NodeSyntax.isName(parts.get(parts.size() - 1).kind());
  }

  /**
   * The argument nodes of a call, which sit directly under the arguments or in an argument list.
   */
  private static List<SyntaxNode> argumentValues(final SyntaxNode arguments) {
    final List<SyntaxNode> values = new ArrayList<>();
    collectArguments(arguments, values);
    return values;
  }

  private static void collectArguments(final SyntaxNode node, final List<SyntaxNode> values) {
    for (final SyntaxNode child : node.children()) {
      if (ARGUMENT.equals(child.kind())) {
        values.add(child);
      } else if (ARGUMENT_LIST.equals(child.kind())) {
        collectArguments(child, values);
      }
    }
  }

  private static List<Span> spans(final List<SyntaxNode> nodes) {
    return nodes.stream().map(SyntaxNode::span).toList();
  }
}
