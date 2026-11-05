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
  private static final Set<String> MEMBER_TOKENS = Set.of(".", "?.");
  private static final int MEMBER_PARTS = 3;

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
      NodeStatementCalls.callOf(node)
          .ifPresentOrElse(
              call -> call(call.callee(), call.arguments(), found), () -> children(node, found));
    } else {
      children(node, found);
      if (VARIABLE_DECLARATION.equals(node.kind())) {
        types.declareVariable(node);
      }
    }
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
    } else if (isMember(parts)) {
      walk(parts.get(0), found);
      found.add(siteOf(FlowSiteKind.MEMBER_READ, node, parts, List.of()));
    } else {
      children(node, found);
    }
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
      found.add(
          new FlowSite(
              FlowSiteKind.CALL,
              NodeFlowSyntax.text(callee),
              "",
              Optional.empty(),
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
