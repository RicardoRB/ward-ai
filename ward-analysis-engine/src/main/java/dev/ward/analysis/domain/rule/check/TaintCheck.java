package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.flow.taint.TaintFile;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.Confidence;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reports each value from a source that reaches a sink argument, with its path and its confidence.
 * The message may use {@code {source}} for the source name and {@code {sink}} for the sink name. A
 * finding whose flow crosses a step that the analysis assumed is {@link Confidence#POSSIBLE}, and
 * it is reported only when the minimum confidence allows it; by default only proven flows are.
 */
public final class TaintCheck implements RuleCheck {
  private static final String SOURCE = "{source}";
  private static final String SINK = "{sink}";

  private final TaintSpec spec;
  private final String message;
  private final Confidence minimum;

  /**
   * Creates the check that reports proven flows only.
   *
   * @param spec the taint specification, already loaded
   * @param message the message of each finding
   */
  public TaintCheck(final TaintSpec spec, final String message) {
    this(spec, message, Confidence.PROVEN);
  }

  /**
   * Creates the check.
   *
   * @param spec the taint specification, already loaded
   * @param message the message of each finding
   * @param minimum the least confidence a reported finding has: {@link Confidence#PROVEN} reports
   *     proven flows only, {@link Confidence#POSSIBLE} reports assumed ones too
   */
  public TaintCheck(final TaintSpec spec, final String message, final Confidence minimum) {
    this.spec = spec;
    this.message = message;
    this.minimum = minimum;
  }

  /**
   * Always fails: the taint check reads the control-flow graphs of the unit, which only {@link
   * #matches(SyntaxNode, FlowView)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the taint check needs the data-flow graphs");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final List<RuleMatch> found = new ArrayList<>();
    TaintFile.run(flow.graphs(), flow.related(), spec).stream()
        .filter(finding -> finding.confidence().compareTo(minimum) <= 0)
        .forEach(finding -> found.add(match(finding)));
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  @Override
  public Optional<TaintSpec> taintSpec() {
    return Optional.of(spec);
  }

  private RuleMatch match(final TaintFinding finding) {
    final String text =
        message.replace(SOURCE, finding.source()).replace(SINK, finding.sink().name());
    return new RuleMatch(
        finding.sink().span(),
        text,
        finding.path(),
        finding.confidence(),
        finding.assumptions()
    );
  }
}
