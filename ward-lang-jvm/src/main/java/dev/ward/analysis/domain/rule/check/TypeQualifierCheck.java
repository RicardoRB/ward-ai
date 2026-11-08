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
 * Reports one SpotBugs {@code TQ} type qualifier pattern from a data-flow over the control-flow
 * graph of each body. Only type qualifiers declared in the file take part; a value whose qualifier
 * is not known is never reported. It lowers the control flow itself, since a rule reads either
 * types or flow.
 */
public final class TypeQualifierCheck implements RuleCheck {

  /** The patterns the check can report. */
  public enum Mode {
    /** A value that always has the qualifier reaches a place that requires it never. */
    ALWAYS_VALUE_USED_WHERE_NEVER_REQUIRED(QualPattern.ALWAYS_VALUE_USED_WHERE_NEVER_REQUIRED),
    /** A value that never has the qualifier reaches a place that requires it always. */
    NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED(QualPattern.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED),
    /** An unknown value reaches a place that strictly requires the qualifier. */
    UNKNOWN_VALUE_USED_WHERE_ALWAYS_STRICTLY_REQUIRED(
        QualPattern.UNKNOWN_VALUE_USED_WHERE_ALWAYS_STRICTLY_REQUIRED),
    /** Two values with conflicting qualifiers are compared. */
    COMPARING_VALUES_WITH_INCOMPATIBLE_TYPE_QUALIFIERS(
        QualPattern.COMPARING_VALUES_WITH_INCOMPATIBLE_TYPE_QUALIFIERS),
    /** A value that may have the qualifier reaches a place that requires it always. */
    MAYBE_SOURCE_VALUE_REACHES_ALWAYS_SINK(QualPattern.MAYBE_SOURCE_VALUE_REACHES_ALWAYS_SINK),
    /** A value that may have the qualifier reaches a place that requires it never. */
    MAYBE_SOURCE_VALUE_REACHES_NEVER_SINK(QualPattern.MAYBE_SOURCE_VALUE_REACHES_NEVER_SINK),
    /** A parameter explicitly marked unknown reaches a place that requires it always. */
    EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_ALWAYS_SINK(
        QualPattern.EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_ALWAYS_SINK),
    /** A parameter explicitly marked unknown reaches a place that requires it never. */
    EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_NEVER_SINK(
        QualPattern.EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_NEVER_SINK);

    private final QualPattern pattern;

    Mode(final QualPattern pattern) {
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
  public TypeQualifierCheck(final Mode mode, final String message) {
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
    return QualifierAnalysis.of(root, unit, types).stream()
        .filter(found -> found.pattern() == mode.pattern)
        .map(found -> CheckSupport.at(found.at(), message))
        .toList();
  }
}
