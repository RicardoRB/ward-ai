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
 * Reports each assignment whose target is a parameter of the method or constructor it sits in, as
 * Checkstyle's ParameterAssignment does. Only a plain name is a target, so assigning a field
 * through {@code this} is not reported. A local variable of the same name is not recognized, so an
 * assignment to a shadowing local is reported too. Lambda parameters are not checked.
 */
public final class ParameterAssignmentCheck implements UnitCheck {

  // shortcut: an assignment to a local that shadows a parameter is reported, upgrade by resolving
  // the local's declaration scope before reporting.
  private static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$]*");
  private static final Set<String> PARAMETER_OWNERS = Set.of("formalParameters");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the parameter's name
   */
  public ParameterAssignmentCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** A parameter and the method or constructor that declares it. */
  private record Parameter(String name, SyntaxNode callable) {}

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<Parameter> parameters = parameters(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      final SyntaxNode assignment = visit.node();
      final Optional<SyntaxNode> callable = Callables.nearest(visit.ancestors());
      if (!Assignments.isAssignment(assignment) || callable.isEmpty()) {
        continue;
      }
      final String target = Assignments.target(assignment);
      if (NAME.matcher(target).matches() && isParameter(parameters, target, callable.get())) {
        matches.add(
            CheckSupport.at(
                assignment.children().get(1).span(), CheckSupport.fill(message, target, "")));
      }
    }
    return List.copyOf(matches);
  }

  private static List<Parameter> parameters(final SyntaxNode root) {
    final List<Parameter> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, PARAMETER_OWNERS)) {
      final Optional<SyntaxNode> callable = Callables.nearest(visit.ancestors());
      if (callable.isPresent()) {
        for (final SyntaxNode parameter : SyntaxWalk.descendants(visit.node())) {
          if ("formalParameter".equals(parameter.kind())) {
            SyntaxWalk.firstDescendant(parameter, "variableDeclaratorId", Set.of())
                .ifPresent(id -> found.add(new Parameter(SyntaxWalk.text(id), callable.get())));
          }
        }
      }
    }
    return found;
  }

  private static boolean isParameter(
      final List<Parameter> parameters, final String name, final SyntaxNode callable) {
    return parameters.stream()
        .anyMatch(parameter -> parameter.name().equals(name) && parameter.callable() == callable);
  }
}
