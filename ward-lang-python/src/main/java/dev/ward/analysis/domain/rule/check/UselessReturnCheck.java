package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Reports a bare {@code return} or {@code return None} as the last statement of a function. */
public final class UselessReturnCheck implements RuleCheck {

  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Set<String> DESCEND =
      Set.of("block", "stmt", "simple_stmts", "simple_stmt", "flow_stmt");
  private static final Set<String> BARE = Set.of("return", "returnNone");
  private static final String BLOCK = "block";
  private static final String RETURN = "return_stmt";

  private final String message;

  /** Creates the check with the message of each finding. */
  public UselessReturnCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final Optional<SyntaxNode> last = lastStatement(visit.node());
      if (last.isPresent() && BARE.contains(SyntaxWalk.text(last.get()))) {
        found.add(new RuleMatch(last.get().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** The return statement that ends the body of {@code function}, if the body ends with one. */
  private static Optional<SyntaxNode> lastStatement(final SyntaxNode function) {
    Optional<SyntaxNode> block = FunctionBodies.child(function, BLOCK);
    while (block.isPresent()) {
      final Optional<SyntaxNode> last = lastPart(block.get());
      if (last.isEmpty()) {
        return Optional.empty();
      }
      if (RETURN.equals(last.get().kind())) {
        return last;
      }
      block = DESCEND.contains(last.get().kind()) ? last : Optional.empty();
    }
    return Optional.empty();
  }

  /** The last child of {@code node} that is a rule rather than a token. */
  private static Optional<SyntaxNode> lastPart(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> !child.children().isEmpty())
        .reduce((a, b) -> b);
  }
}
