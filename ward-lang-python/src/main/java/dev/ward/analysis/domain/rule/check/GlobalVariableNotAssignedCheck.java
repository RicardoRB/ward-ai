package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports a {@code global} name that the function never assigns, like Pylint W0602. */
public final class GlobalVariableNotAssignedCheck implements RuleCheck {

  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Set<String> GLOBALS = Set.of("global_stmt");
  private static final String NAME = "name";

  private final String message;

  /** Creates the check with the message of each finding. */
  public GlobalVariableNotAssignedCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final Set<String> assigned = FunctionBodies.assignedNames(visit.node());
      for (final SyntaxNode global : FunctionBodies.ownScope(visit.node(), GLOBALS)) {
        if (globals(global).stream().anyMatch(name -> !assigned.contains(name))) {
          found.add(new RuleMatch(global.span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private static List<String> globals(final SyntaxNode global) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode child : global.children()) {
      if (NAME.equals(child.kind())) {
        names.add(SyntaxWalk.text(child));
      }
    }
    return names;
  }
}
