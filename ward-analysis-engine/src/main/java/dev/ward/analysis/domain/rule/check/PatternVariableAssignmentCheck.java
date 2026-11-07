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
 * Reports each assignment to a pattern variable, the name an {@code instanceof} pattern declares,
 * as Checkstyle's PatternVariableAssignment does. The assignment is matched by the name's text
 * within the method or constructor the pattern appears in.
 *
 * <p>Shortcut: a local or field of the same name assigned in that method is reported as well, since
 * the check does not resolve which declaration an assignment refers to.
 */
public final class PatternVariableAssignmentCheck implements UnitCheck {

  private static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$]*");
  private static final Set<String> CALLABLES =
      Set.of("methodDeclaration", "constructorDeclaration");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the variable's name
   */
  public PatternVariableAssignmentCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** A pattern variable and the method or constructor it is declared in. */
  private record PatternVariable(String name, SyntaxNode callable) {}

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<PatternVariable> variables = patternVariables(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      final SyntaxNode assignment = visit.node();
      final Optional<SyntaxNode> callable = nearestCallable(visit.ancestors());
      if (Assignments.isAssignment(assignment) && callable.isPresent()) {
        final String target = Assignments.target(assignment);
        if (NAME.matcher(target).matches() && declares(variables, target, callable.get())) {
          matches.add(
              CheckSupport.at(
                  assignment.children().get(1).span(), CheckSupport.fill(message, target, "")));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static List<PatternVariable> patternVariables(final SyntaxNode root) {
    final List<PatternVariable> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("pattern"))) {
      final Optional<SyntaxNode> callable = nearestCallable(visit.ancestors());
      if (callable.isPresent()) {
        for (final SyntaxNode id : SyntaxWalk.descendants(visit.node())) {
          if ("variableDeclaratorId".equals(id.kind())) {
            found.add(new PatternVariable(SyntaxWalk.text(id), callable.get()));
          }
        }
      }
    }
    return found;
  }

  private static boolean declares(
      final List<PatternVariable> variables, final String name, final SyntaxNode callable) {
    return variables.stream()
        .anyMatch(variable -> variable.name().equals(name) && variable.callable() == callable);
  }

  private static Optional<SyntaxNode> nearestCallable(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (CALLABLES.contains(ancestors.get(index).kind())) {
        return Optional.of(ancestors.get(index));
      }
    }
    return Optional.empty();
  }
}
