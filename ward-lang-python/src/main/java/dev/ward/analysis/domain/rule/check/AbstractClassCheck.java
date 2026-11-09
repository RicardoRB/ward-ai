package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports, like Pylint {@code abstract-method} and {@code abstract-class-instantiated}, a class of
 * this module that leaves an abstract method unimplemented, and a call that instantiates such an
 * abstract class. Only classes whose bases are known are checked. Shortcut: a method whose body is
 * only {@code pass} is not counted as abstract, only one decorated with {@code abstractmethod}.
 */
public final class AbstractClassCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** A call creates an instance of a class with an unimplemented abstract method. */
    INSTANTIATED,
    /** A concrete class inherits an abstract method and does not override it. */
    METHOD_NOT_OVERRIDDEN
  }

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Pattern CALL = Pattern.compile("^(\\w+)\\(");

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public AbstractClassCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final PythonClassModel model = PythonClassModel.of(root);
    return switch (problem) {
      case INSTANTIATED -> instantiated(root, model);
      case METHOD_NOT_OVERRIDDEN -> notOverridden(model);
    };
  }

  private List<RuleMatch> notOverridden(final PythonClassModel model) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final PythonClassModel.ClassInfo info : model.classes().values()) {
      if (isConcrete(model, info)) {
        for (int index = 0; index < model.unimplemented(info.name()).size(); index++) {
          found.add(new RuleMatch(info.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private static boolean isConcrete(
      final PythonClassModel model, final PythonClassModel.ClassInfo info) {
    return model.order(info.name()).status() == PythonClassModel.Status.KNOWN
        && !info.declaresAbstract();
  }

  private List<RuleMatch> instantiated(final SyntaxNode root, final PythonClassModel model) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      final Matcher call = CALL.matcher(SyntaxWalk.text(visit.node()));
      if (call.find()
          && isAbstractClass(model, call.group(1))
          && !call.group(1).equals(ClassIndex.enclosingClass(visit.ancestors()))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean isAbstractClass(final PythonClassModel model, final String name) {
    return model.classNamed(name).isPresent()
        && model.order(name).status() == PythonClassModel.Status.KNOWN
        && model.isAbc(name)
        && !model.unimplemented(name).isEmpty();
  }
}
