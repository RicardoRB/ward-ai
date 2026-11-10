package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reports a method that overrides a method of this module with a different signature, like Pylint
 * {@code arguments-differ} and {@code arguments-renamed}. Only overrides whose every ancestor is a
 * class of this module are compared; the comparison itself is in {@link ParameterComparison}.
 */
public final class SignatureOverrideCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** The overriding method has a different number of parameters, or drops {@code *args}. */
    DIFFERS,
    /** The overriding method names a positional parameter differently. */
    RENAMED
  }

  private static final Pattern PRIVATE = Pattern.compile("^_{2,10}.*[^_]+_?$");
  private static final String INIT = "__init__";
  private static final String OVERLOAD = "overload";
  private static final String SETTER = "setter";

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public SignatureOverrideCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final PythonClassModel model = PythonClassModel.of(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final PythonClassModel.ClassInfo info : model.classes().values()) {
      if (model.order(info.name()).status() == PythonClassModel.Status.KNOWN) {
        for (final PythonClassModel.Method method : info.methods().values()) {
          found.addAll(checkMethod(model, info, method));
        }
      }
    }
    return List.copyOf(found);
  }

  private List<RuleMatch> checkMethod(
      final PythonClassModel model,
      final PythonClassModel.ClassInfo info,
      final PythonClassModel.Method method) {
    final List<RuleMatch> found = new ArrayList<>();
    final Optional<PythonClassModel.Method> parent = overridden(model, info, method.name());
    if (parent.isEmpty() || skipped(method) || parent.get().decoratedWith(OVERLOAD)) {
      return found;
    }
    for (final ParameterComparison.Kind kind :
        ParameterComparison.differences(parent.get(), method)) {
      if (reports(kind)) {
        found.add(new RuleMatch(method.node().span(), message));
      }
    }
    return found;
  }

  private boolean reports(final ParameterComparison.Kind kind) {
    return switch (problem) {
      case DIFFERS -> kind != ParameterComparison.Kind.RENAMED;
      case RENAMED -> kind == ParameterComparison.Kind.RENAMED;
    };
  }

  /** The first method of an ancestor with this name, when every ancestor is known. */
  private static Optional<PythonClassModel.Method> overridden(
      final PythonClassModel model, final PythonClassModel.ClassInfo info, final String name) {
    final List<String> order = model.order(info.name()).names();
    for (final String ancestor : order.subList(1, order.size())) {
      final PythonClassModel.ClassInfo parent = model.classes().get(ancestor);
      if (parent != null && parent.methods().containsKey(name)) {
        return Optional.of(parent.methods().get(name));
      }
    }
    return Optional.empty();
  }

  private static boolean skipped(final PythonClassModel.Method method) {
    return INIT.equals(method.name())
        || PRIVATE.matcher(method.name()).lookingAt()
        || method.decoratedWith(SETTER)
        || method.decoratedWith(OVERLOAD);
  }
}
