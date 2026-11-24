package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gives a name a clean value where the program has proved it safe: a path that was resolved and
 * then checked with {@code startsWith} (inside the branch that holds, or past a branch that exits
 * when it does not), and the value of a lookup in a table of constants.
 *
 * <p>shortcut: only a bare name that a {@code const}, {@code let} or {@code var} of this body
 * assigned the result of {@code path.resolve}, {@code normalize}, {@code join} or {@code realpath},
 * and a bare name assigned the whole lookup {@code TABLE[key]} or {@code TABLE.get(key)} with one
 * key; upgrade when a project guards a member or a call result.
 */
final class NodeGuards {
  private static final String NAME = "([A-Za-z_$][\\w$]*)";
  private static final Pattern RESOLVED =
      Pattern.compile(
          "^(?:const|let|var)" + NAME + "(?::[^=]+)?=(?:await)?(?:path|fs)\\."
              + "(?:resolve|normalize|join|realpath|realpathSync)\\(.*"
      );
  private static final Pattern LOOKUP =
      Pattern.compile(
          "^(?:const|let|var)" + NAME + "(?::[^=]+)?=" + NAME + "(?:\\[[^\\],]+]|\\.get\\([^,]*\\));?$"
      );
  private static final Pattern STARTS_WITH = Pattern.compile("^" + NAME + "\\.startsWith\\(.+\\)$");
  private static final Pattern NOT_STARTS_WITH = Pattern.compile(
      "^!" + NAME + "\\.startsWith\\(.+\\)$"
  );
  private static final Set<String> EXITS = Set.of("returnStatement", "throwStatement");
  private static final String BLOCK = "block";
  private static final int NAME_GROUP = 1;
  private static final int TABLE_GROUP = 2;
  private static final int OPERATOR_INDEX = 1;
  private static final int RIGHT_INDEX = 2;

  private final ControlFlowBuilder flow;
  private final NodeAccesses accesses;
  private final Set<String> tables;
  private final Set<String> resolved = new HashSet<>();

  NodeGuards(final ControlFlowBuilder flow, final NodeAccesses accesses, final Set<String> tables) {
    this.flow = flow;
    this.accesses = accesses;
    this.tables = Set.copyOf(tables);
  }

  /**
   * Notes a declaration: a name assigned a resolved path can be proved by a later check, and a name
   * assigned a member of a constant table holds a value of the program.
   */
  boolean declared(final SyntaxNode statement) {
    final String text = NodeFlowSyntax.text(statement);
    final Matcher path = RESOLVED.matcher(text);
    if (path.matches()) {
      resolved.add(path.group(NAME_GROUP));
    }
    final Matcher lookup = LOOKUP.matcher(text);
    if (lookup.matches() && tables.contains(lookup.group(TABLE_GROUP))) {
      clean(lookup.group(NAME_GROUP), after(statement.span()));
    }
    return true;
  }

  /** Cleans the path that the condition proves to start with a base, in the branch that holds. */
  void inside(final SyntaxNode condition) {
    conjuncts(condition).stream()
        .map(part -> STARTS_WITH.matcher(NodeFlowSyntax.text(part)))
        .filter(Matcher::matches)
        .map(match -> match.group(NAME_GROUP))
        .filter(resolved::contains)
        .forEach(name -> clean(name, condition.span()));
  }

  /**
   * Cleans the path that the statement proved to start with a base: the branch exits when {@code
   * !path.startsWith(base)} holds, possibly among other alternatives.
   */
  void past(final SyntaxNode condition, final SyntaxNode branch) {
    if (!exits(branch)) {
      return;
    }
    alternatives(condition).stream()
        .map(part -> NOT_STARTS_WITH.matcher(NodeFlowSyntax.text(part)))
        .filter(Matcher::matches)
        .map(match -> match.group(NAME_GROUP))
        .filter(resolved::contains)
        .forEach(name -> clean(name, after(branch.span())));
  }

  private static List<SyntaxNode> conjuncts(final SyntaxNode condition) {
    return split(condition, "&&");
  }

  private static List<SyntaxNode> alternatives(final SyntaxNode condition) {
    return split(condition, "||");
  }

  private static List<SyntaxNode> split(final SyntaxNode condition, final String operator) {
    final SyntaxNode value = NodeFlowSyntax.unwrap(condition);
    if (NodeExpressions.isLogical(value)) {
      final List<SyntaxNode> parts = NodeFlowSyntax.parts(value);
      if (operator.equals(NodeFlowSyntax.text(parts.get(OPERATOR_INDEX)))) {
        final List<SyntaxNode> found = new ArrayList<>(split(parts.get(0), operator));
        found.addAll(split(parts.get(RIGHT_INDEX), operator));
        return found;
      }
    }
    return List.of(value);
  }

  /** Whether the last statement of the branch returns or throws. */
  private static boolean exits(final SyntaxNode branch) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(branch);
    SyntaxNode part = parts.isEmpty() ? branch : parts.get(0);
    if (BLOCK.equals(part.kind())) {
      final List<SyntaxNode> statements = NodeFlowSyntax.statements(part);
      if (statements.isEmpty()) {
        return false;
      }
      part = NodeFlowSyntax.parts(statements.get(statements.size() - 1)).get(0);
    }
    return EXITS.contains(part.kind());
  }

  private void clean(final String name, final Span span) {
    final List<VariableAccess> clean = accesses.scopes.clean(name, span);
    if (!clean.isEmpty()) {
      flow.record(clean, List.of());
    }
  }

  /** A span just past a statement, which no statement contains, so its write takes no taint. */
  private static Span after(final Span statement) {
    return new Span(
        statement.endLine(),
        statement.endColumn(),
        statement.endLine(),
        statement.endColumn() + 1,
        statement.endOffset(),
        statement.endOffset() + 1
    );
  }
}
