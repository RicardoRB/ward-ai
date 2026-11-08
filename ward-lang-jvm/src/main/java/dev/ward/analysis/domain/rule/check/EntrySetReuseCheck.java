package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports an {@code add(entry)} of the loop variable in a for-each over {@code entrySet()}: a map
 * may reuse one entry object for the whole iteration, so a stored entry can change later. Copy the
 * key and value instead.
 */
public final class EntrySetReuseCheck implements UnitCheck {
  private static final String FOR = "'for'";
  private static final String STATEMENT = "statement";
  private static final String VARIABLE_ID = "variableDeclaratorId";
  private static final String EXPRESSION = "expression";
  private static final String ENTRY_SET = ".entrySet()";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the add call
   */
  public EntrySetReuseCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(node -> !node.children().isEmpty() && FOR.equals(node.children().get(0).kind()))
        .flatMap(loop -> adds(loop).stream())
        .map(add -> CheckSupport.at(add.span(), message))
        .toList();
  }

  private static List<SyntaxNode> adds(final SyntaxNode loop) {
    final Optional<SyntaxNode> variable =
        SyntaxWalk.descendants(loop).stream().filter(n -> VARIABLE_ID.equals(n.kind())).findFirst();
    final boolean overEntries =
        SyntaxWalk.descendants(loop).stream()
            .anyMatch(n -> EXPRESSION.equals(n.kind()) && SyntaxWalk.text(n).endsWith(ENTRY_SET));
    if (variable.isEmpty() || !overEntries) {
      return List.of();
    }
    final Pattern add =
        Pattern.compile(
            "^[\\w.]+\\.add\\(" + Pattern.quote(SyntaxWalk.text(variable.get())) + "\\)$");
    return SyntaxWalk.descendants(loop.children().get(loop.children().size() - 1)).stream()
        .filter(n -> EXPRESSION.equals(n.kind()) && add.matcher(SyntaxWalk.text(n)).matches())
        .toList();
  }
}
