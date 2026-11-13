package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.flow.taint.TaintFile;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reports each value from a source that reaches a sink argument, with its path. The message may use
 * {@code {source}} for the source name and {@code {sink}} for the sink name.
 */
public final class TaintCheck implements RuleCheck {

  private static final String SOURCE = "{source}";
  private static final String SINK = "{sink}";

  private final TaintSpec spec;
  private final String message;

  /**
   * Creates the check.
   *
   * @param spec the taint specification, already loaded
   * @param message the message of each finding
   */
  public TaintCheck(final TaintSpec spec, final String message) {
    this.spec = spec;
    this.message = message;
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
    TaintFile.run(flow.graphs(), spec).forEach(finding -> found.add(match(finding)));
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private RuleMatch match(final TaintFinding finding) {
    final String text =
        message.replace(SOURCE, finding.source()).replace(SINK, finding.sink().name());
    return new RuleMatch(finding.sink().span(), text, finding.path());
  }
}
