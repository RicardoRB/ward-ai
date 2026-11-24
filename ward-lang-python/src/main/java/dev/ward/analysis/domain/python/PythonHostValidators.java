package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonTokens.Call;
import dev.ward.analysis.domain.python.PythonTokens.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Proves, from the body of a function, that it returns true for a parameter only when the host of
 * a value made from the parameter is the host of the application. It marks each return of the body
 * for each parameter it holds for, and the taint analysis decides, with the marks of the functions
 * it calls, which functions validate which arguments.
 *
 * <p>A value is made from the parameter when a name is assigned an expression that mentions it, in
 * any order. A host is read as {@code .netloc} or {@code .hostname} of such a value. A return is
 * proved when
 *
 * <ul>
 *   <li>it returns nothing, {@code False} or {@code None}; or
 *   <li>it follows a statement of the body of the function, at its top level, of the form {@code if
 *       <rejection>: return False}, where a rejection is a host read alone (the value has a host, so
 *       it is not relative), or a host read compared with {@code !=} or {@code not in}; or
 *   <li>it is a chain of {@code and} one of whose parts compares a host read with {@code ==} or
 *       {@code in}.
 * </ul>
 *
 * A return that is a chain of {@code and} one of whose parts calls a function on a value made from
 * the parameter is proved when that function validates that argument. Any other return leaves the
 * parameter unproved, so a function that can return true without a check marks nothing.
 *
 * <p>shortcut: a rejection or a comparison on a value read from an attribute, call or name chain,
 * and a rejection as a top-level statement only; upgrade with dominance in the graph.
 */
final class PythonHostValidators {
  /** What the marks say the function proves. */
  static final String WHAT = "host";

  private static final String RETURN_MARK = "<validated-return>";
  private static final String SAFE = "safe";
  private static final String CALLS = "calls";
  private static final String FALSE = "False";
  private static final String NONE = "None";
  private static final String RETURN = "return";
  private static final String RETURN_STMT = "return_stmt";
  private static final Set<String> NESTED = Set.of("funcdef", "classdef", "lambdef", "decorated");

  private PythonHostValidators() {}

  /** The marks of the returns of a body, for each of its parameters that it validates. */
  static List<FlowSite> marks(final SyntaxNode block, final List<String> parameters) {
    final List<SyntaxNode> returns = new ArrayList<>();
    final List<SyntaxNode> top = PythonStatementLeaves.leaves(block);
    final List<Integer> where = new ArrayList<>();
    for (int index = 0; index < top.size(); index++) {
      final int before = returns.size();
      collectReturns(top.get(index), returns);
      for (int added = before; added < returns.size(); added++) {
        where.add(index);
      }
    }
    if (returns.isEmpty() || parameters.isEmpty()) {
      return List.of();
    }
    final List<List<String>> assigned = PythonDerivedNames.assignments(block);
    final List<FlowSite> marks = new ArrayList<>();
    for (final String parameter : parameters) {
      marks.addAll(forParameter(parameter, top, returns, where, assigned));
    }
    return marks;
  }

  private static List<FlowSite> forParameter(
      final String parameter,
      final List<SyntaxNode> top,
      final List<SyntaxNode> returns,
      final List<Integer> where,
      final List<List<String>> assigned
  ) {
    final Set<String> made = PythonDerivedNames.madeFrom(parameter, assigned);
    final int guard = firstRejection(top, made);
    final List<FlowSite> marks = new ArrayList<>();
    for (int index = 0; index < returns.size(); index++) {
      final Optional<FlowSite> mark =
          mark(parameter, returns.get(index), guard >= 0 && where.get(index) > guard, made);
      if (mark.isEmpty()) {
        return List.of();
      }
      marks.add(mark.get());
    }
    return marks;
  }

  private static Optional<FlowSite> mark(
      final String parameter,
      final SyntaxNode statement,
      final boolean guarded,
      final Set<String> made
  ) {
    final List<SyntaxNode> nodes = PythonSyntax.tokens(statement);
    final List<String> tokens = PythonTokens.texts(nodes);
    final Range value = new Range(1, tokens.size());
    final boolean empty = value.to() <= value.from();
    if (guarded || empty || isNothing(tokens, value)) {
      return Optional.of(site(parameter, SAFE, List.of(), statement.span()));
    }
    if (PythonTokens.has(tokens, value, "or")) {
      return Optional.empty();
    }
    final List<Range> conjuncts = PythonTokens.split(tokens, value, "and");
    if (conjuncts.stream().anyMatch(part -> PythonHostReads.comparesHost(tokens, part, made))) {
      return Optional.of(site(parameter, SAFE, List.of(), statement.span()));
    }
    final List<Span> pairs = new ArrayList<>();
    for (final Range part : conjuncts) {
      PythonTokens.call(tokens, part).ifPresent(
          call -> pairs.addAll(derived(nodes, tokens, call, made))
      );
    }
    return pairs.isEmpty()
        ? Optional.empty()
        : Optional.of(site(parameter, CALLS, pairs, statement.span()));
  }

  /** The span of the call and of each argument made from the parameter, in pairs. */
  private static List<Span> derived(
      final List<SyntaxNode> nodes,
      final List<String> tokens,
      final Call call,
      final Set<String> made
  ) {
    final List<Span> pairs = new ArrayList<>();
    for (final Range argument : call.arguments()) {
      if (PythonTokens.mentions(tokens, argument, made)) {
        pairs.add(PythonTokens.span(nodes, call.name()));
        pairs.add(PythonTokens.span(nodes, argument));
      }
    }
    return pairs;
  }

  private static FlowSite site(
      final String parameter,
      final String how,
      final List<Span> arguments,
      final Span span
  ) {
    return new FlowSite(
        FlowSiteKind.CALL,
        RETURN_MARK,
        parameter,
        Optional.empty(),
        List.of(WHAT, how),
        arguments,
        span
    );
  }

  private static boolean isNothing(final List<String> tokens, final Range value) {
    return value.to() - value.from() == 1
        && (FALSE.equals(tokens.get(1)) || NONE.equals(tokens.get(1)));
  }

  /** The index of the first top-level statement {@code if <rejection>: return False}, or -1. */
  private static int firstRejection(final List<SyntaxNode> top, final Set<String> made) {
    for (int index = 0; index < top.size(); index++) {
      if (rejects(top.get(index), made)) {
        return index;
      }
    }
    return -1;
  }

  private static boolean rejects(final SyntaxNode statement, final Set<String> made) {
    if (!"if_stmt".equals(statement.kind())) {
      return false;
    }
    final List<SyntaxNode> conditions = PythonFlowSyntax.children(statement, "namedexpr_test");
    final List<SyntaxNode> blocks = PythonFlowSyntax.children(statement, "block");
    if (conditions.size() != 1 || blocks.size() != 1) {
      return false;
    }
    final List<SyntaxNode> leaves = PythonStatementLeaves.leaves(blocks.get(0));
    final List<String> last =
        leaves.isEmpty() ? List.of() : PythonLiteralCollections.words(
            leaves.get(leaves.size() - 1)
        );
    if (!List.of(RETURN, FALSE).equals(last)) {
      return false;
    }
    final List<String> tokens = PythonLiteralCollections.words(conditions.get(0));
    return PythonTokens.split(tokens, new Range(0, tokens.size()), "or").stream()
        .anyMatch(part -> PythonHostReads.rejection(tokens, part, made));
  }

  private static void collectReturns(final SyntaxNode node, final List<SyntaxNode> out) {
    if (NESTED.contains(node.kind())) {
      return;
    }
    if (RETURN_STMT.equals(node.kind())) {
      out.add(node);
      return;
    }
    node.children().forEach(child -> collectReturns(child, out));
  }
}
