package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a method whose body is a single statement that calls the method itself with no condition
 * on the way, as Error Prone's InfiniteRecursion does for the plain case. A call inside a
 * conditional, a loop or a second statement is not read.
 */
public final class InfiniteRecursionCheck implements RuleCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public InfiniteRecursionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("methodDeclaration"))) {
      final String name = child(visit.node(), "identifier").map(SyntaxWalk::text).orElse("");
      if (!name.isEmpty() && callsItself(visit.node(), name)) {
        matches.add(CheckSupport.at(visit.node().span(), CheckSupport.fill(message, name, "")));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean callsItself(final SyntaxNode method, final String name) {
    final Pattern call =
        Pattern.compile("^(?:return)?(?:this\\.)?" + Pattern.quote(name) + "\\([^;{}?&|]*\\);$");
    return child(method, "methodBody")
        .flatMap(body -> child(body, "block"))
        .map(
            block ->
                block.children().stream().filter(c -> "blockStatement".equals(c.kind())).toList())
        .filter(statements -> statements.size() == 1)
        .map(statements -> call.matcher(SyntaxWalk.text(statements.get(0))).matches())
        .orElse(false);
  }

  private static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst();
  }
}
