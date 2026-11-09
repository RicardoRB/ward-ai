package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports a loop or except target that rebinds an argument of its function, like Pylint R1704. */
public final class RedefinedArgumentFromLocalCheck implements RuleCheck {

  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Set<String> LOOPS = Set.of("for_stmt");
  private static final Set<String> HANDLERS = Set.of("except_clause");
  private static final String NAME = "name";

  private final String message;

  /** Creates the check with the message of each finding. */
  public RedefinedArgumentFromLocalCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final Set<String> arguments = FunctionBodies.parameterNames(visit.node());
      for (final SyntaxNode loop : FunctionBodies.ownScope(visit.node(), LOOPS)) {
        if (targetsArgument(loop, arguments)) {
          found.add(new RuleMatch(loop.span(), message));
        }
      }
      for (final SyntaxNode handler : FunctionBodies.ownScope(visit.node(), HANDLERS)) {
        if (boundName(handler).filter(arguments::contains).isPresent()) {
          found.add(new RuleMatch(handler.span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private static boolean targetsArgument(final SyntaxNode loop, final Set<String> arguments) {
    return FunctionBodies.child(loop, "exprlist")
        .map(SyntaxWalk::text)
        .map(FunctionBodies::names)
        .map(names -> names.stream().anyMatch(arguments::contains))
        .orElse(false);
  }

  /** The name an except clause binds with {@code as}, if it binds one. */
  private static java.util.Optional<String> boundName(final SyntaxNode handler) {
    String bound = null;
    for (final SyntaxNode child : handler.children()) {
      if (NAME.equals(child.kind())) {
        bound = SyntaxWalk.text(child);
      }
    }
    return java.util.Optional.ofNullable(bound);
  }
}
