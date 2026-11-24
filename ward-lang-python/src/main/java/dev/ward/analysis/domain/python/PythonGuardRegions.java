package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonTokens.Call;
import dev.ward.analysis.domain.python.PythonTokens.Range;
import dev.ward.analysis.domain.python.PythonValidatedSites.Use;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tracks the statements in which a condition has proved a value with a validator of the program:
 * the branch a condition guards, the else branch of a negated one, and the statements past a
 * branch that exits.
 */
final class PythonGuardRegions {
  private static final Set<String> COMPARISONS = Set.of("==", "!=", "<=", ">=");
  private static final String IDENTIFIER = "[A-Za-z_]\\w*";

  private final PythonValidatedSites sites;
  private List<Use> carried = List.of();

  PythonGuardRegions(final PythonValidatedSites sites) {
    this.sites = sites;
  }

  /** Enters the branch that {@code condition} guards, which {@code block} holds. */
  void enterInside(final SyntaxNode condition, final SyntaxNode block) {
    final List<String> tokens = PythonLiteralCollections.words(condition);
    sites.enter(stable(uses(PythonValidatedGuards.inside(tokens), tokens), List.of(block)));
  }

  /** Enters the else branch of a lone condition, which holds when each negated call was true. */
  void enterElse(final SyntaxNode condition, final SyntaxNode block, final boolean lone) {
    final List<String> tokens = PythonLiteralCollections.words(condition);
    final List<Call> calls = lone ? PythonValidatedGuards.past(tokens) : List.of();
    sites.enter(stable(uses(calls, tokens), List.of(block)));
  }

  /** Ends the region that was entered last. */
  void leave() {
    sites.leave();
  }

  /** Remembers that the statements after the one being lowered rely on the condition. */
  void carry(final SyntaxNode condition) {
    final List<String> tokens = PythonLiteralCollections.words(condition);
    carried = uses(PythonValidatedGuards.past(tokens), tokens);
  }

  /**
   * Enters the region of what was carried, for the statements that follow; returns whether there
   * was one, which the caller must leave when the statements end.
   */
  boolean enterCarried(final List<SyntaxNode> following) {
    if (carried.isEmpty()) {
      return false;
    }
    sites.enter(stable(carried, following));
    carried = List.of();
    return true;
  }

  /**
   * The uses whose expression the statements do not assign: a name given another value in the
   * region is no longer the one that the guard checked. shortcut: an assignment anywhere in the
   * region drops the use, whatever its order; upgrade with the flow of the graph.
   */
  private static List<Use> stable(final List<Use> uses, final List<SyntaxNode> region) {
    final List<String> tokens = new ArrayList<>();
    region.forEach(node -> tokens.addAll(PythonLiteralCollections.words(node)));
    return uses.stream().filter(use -> !assigns(tokens, use.tokens().get(0))).toList();
  }

  private static boolean assigns(final List<String> tokens, final String name) {
    for (int index = 0; index < tokens.size(); index++) {
      if (name.equals(tokens.get(index)) && assignedAt(tokens, index)) {
        return true;
      }
    }
    return false;
  }

  private static boolean assignedAt(final List<String> tokens, final int index) {
    final String before = index > 0 ? tokens.get(index - 1) : "";
    final String next = index + 1 < tokens.size() ? tokens.get(index + 1) : "";
    final boolean attribute = ".".equals(before);
    final boolean assignment = next.endsWith("=") && !COMPARISONS.contains(next);
    return !attribute && (assignment || "as".equals(before) || "for".equals(before));
  }

  /** The uses of the arguments of the guard calls that a branch can rely on. */
  private List<Use> uses(final List<Call> calls, final List<String> tokens) {
    return calls.stream()
        .flatMap(call -> usesOf(call, tokens).stream())
        .toList();
  }

  private List<Use> usesOf(final Call call, final List<String> tokens) {
    final List<Use> uses = new ArrayList<>();
    final int positional = PythonValidatedGuards.positional(tokens, call);
    for (int index = 0; index < positional; index++) {
      final Range argument = call.arguments().get(index);
      final List<String> expression = tokens.subList(argument.from(), argument.to());
      sites
          .use(call.callee(), index, call.arguments().size(), expression)
          .filter(use -> use.tokens().stream().anyMatch(token -> token.matches(IDENTIFIER)))
          .ifPresent(uses::add);
    }
    return uses;
  }
}
