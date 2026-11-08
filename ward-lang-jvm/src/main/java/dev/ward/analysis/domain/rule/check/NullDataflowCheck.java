package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports one SpotBugs {@code NP} or {@code RCN} null pattern from a data-flow over the
 * control-flow graph of each body in the file. A variable whose value the analysis does not know is
 * never reported. It lowers the control flow itself, since a rule reads either types or flow.
 */
public final class NullDataflowCheck implements RuleCheck {

  /** The patterns the check can report. */
  public enum Mode {
    /** A variable that is null on every path is dereferenced. */
    ALWAYS_NULL(NullAnalysis.Pattern.ALWAYS_NULL),
    /** A variable that is null on an exception path is dereferenced. */
    ALWAYS_NULL_EXCEPTION(NullAnalysis.Pattern.ALWAYS_NULL_EXCEPTION),
    /** {@code close()} is called on a variable that is null. */
    CLOSING_NULL(NullAnalysis.Pattern.CLOSING_NULL),
    /** A parameter marked possibly null is dereferenced without a check. */
    ARGUMENT_MIGHT_BE_NULL(NullAnalysis.Pattern.ARGUMENT_MIGHT_BE_NULL),
    /** Null is passed for a parameter that is annotated never to be null. */
    NONNULL_PARAM_VIOLATION(NullAnalysis.Pattern.NONNULL_PARAM_VIOLATION),
    /** A null that is certain to reach a parameter that must not be null. */
    NULL_PARAM_DEREF(NullAnalysis.Pattern.NULL_PARAM_DEREF),
    /** Null is passed to a parameter that a non-virtual callee dereferences. */
    NULL_PARAM_DEREF_NONVIRTUAL(NullAnalysis.Pattern.NULL_PARAM_DEREF_NONVIRTUAL),
    /** Null is passed to a parameter that every possible callee dereferences. */
    NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS(
        NullAnalysis.Pattern.NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS),
    /** Null is stored into a field annotated never to be null. */
    STORE_INTO_NONNULL_FIELD(NullAnalysis.Pattern.STORE_INTO_NONNULL_FIELD),
    /** Null is returned from a method annotated never to return null. */
    NONNULL_RETURN_VIOLATION(NullAnalysis.Pattern.NONNULL_RETURN_VIOLATION),
    /** A null on some path is certain to be dereferenced at one place. */
    NULL_ON_SOME_PATH(NullAnalysis.Pattern.NULL_ON_SOME_PATH),
    /** The same, in code that only an exception reaches. */
    NULL_ON_SOME_PATH_EXCEPTION(NullAnalysis.Pattern.NULL_ON_SOME_PATH_EXCEPTION),
    /** A null on some path is certain to be dereferenced, at one of several places. */
    GUARANTEED_DEREF(NullAnalysis.Pattern.GUARANTEED_DEREF),
    /** The same, in code that only an exception reaches. */
    GUARANTEED_DEREF_ON_EXCEPTION_PATH(NullAnalysis.Pattern.GUARANTEED_DEREF_ON_EXCEPTION_PATH),
    /** A value is compared with null after it was dereferenced. */
    WOULD_HAVE_BEEN_NPE(NullAnalysis.Pattern.WOULD_HAVE_BEEN_NPE);

    private final NullAnalysis.Pattern pattern;

    Mode(final NullAnalysis.Pattern pattern) {
      this.pattern = pattern;
    }
  }

  private static final FactNeed NEED = new FactNeed(Set.of("expression"), Set.of(FactKind.CALL));

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode the pattern to report
   * @param message the message reported at the finding
   */
  public NullDataflowCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return NullAnalysis.of(root, unit, types).stream()
        .filter(finding -> finding.pattern() == mode.pattern)
        .map(finding -> CheckSupport.at(finding.at(), message))
        .toList();
  }
}
