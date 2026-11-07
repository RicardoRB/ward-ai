package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports one kind of finding of the nullness data-flow analysis: a null dereference, a redundant
 * null check, a null that reaches a sink that must not be null, or a mismatch between nullness
 * qualifiers. It runs on the control-flow graphs of the unit, and a variable with an unknown value
 * is never reported.
 */
public final class FlowNullnessCheck implements RuleCheck {

  private static final Map<String, FlowNullEvent.Kind> KINDS =
      Map.ofEntries(
          Map.entry("np-always-null", FlowNullEvent.Kind.DEREF_NULL),
          Map.entry("np-always-null-exception", FlowNullEvent.Kind.DEREF_NULL_EXCEPTION),
          Map.entry("np-null-on-some-path", FlowNullEvent.Kind.DEREF_MIXED),
          Map.entry("np-null-on-some-path-exception", FlowNullEvent.Kind.DEREF_MIXED_EXCEPTION),
          Map.entry("np-closing-null", FlowNullEvent.Kind.CLOSE_NULL),
          Map.entry("np-null-instanceof", FlowNullEvent.Kind.INSTANCEOF_NULL),
          Map.entry(
              "rcn-redundant-nullcheck-would-have-been-a-npe",
              FlowNullEvent.Kind.CHECK_AFTER_DEREF),
          Map.entry("np-nonnull-param-violation", FlowNullEvent.Kind.PARAM_NULL),
          Map.entry("np-nonnull-return-violation", FlowNullEvent.Kind.RETURN_NULL),
          Map.entry("np-store-into-nonnull-field", FlowNullEvent.Kind.FIELD_NULL),
          Map.entry("np-optional-return-null", FlowNullEvent.Kind.OPTIONAL_RETURN_NULL),
          Map.entry(
              "tq-maybe-source-value-reaches-always-sink", FlowNullEvent.Kind.MAYBE_TO_ALWAYS),
          Map.entry(
              "tq-never-value-used-where-always-required", FlowNullEvent.Kind.NEVER_TO_ALWAYS),
          Map.entry(
              "tq-always-value-used-where-never-required", FlowNullEvent.Kind.ALWAYS_TO_NEVER),
          Map.entry("tq-maybe-source-value-reaches-never-sink", FlowNullEvent.Kind.MAYBE_TO_NEVER),
          Map.entry(
              "tq-comparing-values-with-incompatible-type-qualifiers",
              FlowNullEvent.Kind.COMPARE_INCOMPATIBLE));

  private final FlowNullEvent.Kind kind;
  private final String message;

  private FlowNullnessCheck(final FlowNullEvent.Kind kind, final String message) {
    this.kind = kind;
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Returns the names of the rules this check implements. */
  public static Set<String> names() {
    return KINDS.keySet();
  }

  /**
   * Creates the check for the rule {@code name}.
   *
   * @param name one of {@link #names()}
   * @param message the message; {@code {value}} is the variable or value the finding is about
   * @throws IllegalArgumentException if {@code name} is not a nullness data-flow rule
   */
  public static FlowNullnessCheck of(final String name, final String message) {
    final FlowNullEvent.Kind found = KINDS.get(name);
    if (found == null) {
      throw new IllegalArgumentException("unknown nullness data-flow rule: " + name);
    }
    return new FlowNullnessCheck(found, message);
  }

  /**
   * Always fails: this check reads the control-flow graphs of the unit, which only {@link
   * #matches(SyntaxNode, FlowView)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the nullness checks need the data-flow graphs");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final FlowNullIndex index = FlowNullIndex.of(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final ControlFlowGraph graph : flow.graphs()) {
      FlowNullness.analyze(graph, index).stream()
          .filter(event -> event.kind() == kind)
          .forEach(
              event ->
                  found.add(
                      CheckSupport.at(event.span(), CheckSupport.fill(message, event.name(), ""))));
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }
}
