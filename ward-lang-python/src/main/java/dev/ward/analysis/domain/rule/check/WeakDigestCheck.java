package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Confidence;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reports a call to {@code hashlib.md5} or {@code hashlib.sha1}, unless the digest is proven to be
 * only an identifier. It follows where the digest goes, through variables, string building and the
 * return values of functions of the file, and classifies each use (see {@link WeakDigestVerdict}).
 * A digest that is compared, given to an authentication function, returned to callers that are not
 * seen, or never used for anything is {@link Confidence#PROVEN}. A digest that goes where the
 * analysis does not follow it is {@link Confidence#POSSIBLE}, and is reported only when the minimum
 * confidence allows it. A call with {@code usedforsecurity=False} is not reported.
 */
public final class WeakDigestCheck implements RuleCheck {
  private static final String MODULE = "hashlib";
  private static final TaintSpec SPEC =
      new TaintSpec(List.of(source("md5"), source("sha1")), List.of(), List.of(), List.of());

  private final String message;
  private final Confidence minimum;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   * @param minimum the least confidence of a reported finding
   */
  public WeakDigestCheck(final String message, final Confidence minimum) {
    this.message = message;
    this.minimum = minimum;
  }

  private static TaintEntry source(final String name) {
    return new TaintEntry(
        FlowSiteKind.CALL,
        name,
        Optional.of(MODULE),
        Optional.of(MODULE),
        List.of()
    );
  }

  /**
   * Always fails: the check reads the control-flow graphs of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the weak digest check needs the data-flow graphs");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final FlowSite site : calls(flow.graphs())) {
      if (!WeakDigestVerdict.ignored(root, site)) {
        WeakDigestVerdict.of(root, flow, SPEC, site)
            .filter(verdict -> verdict.confidence().compareTo(minimum) <= 0)
            .map(verdict -> match(site, verdict))
            .ifPresent(found::add);
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  @Override
  public Optional<TaintSpec> taintSpec() {
    return Optional.of(SPEC);
  }

  private RuleMatch match(final FlowSite site, final WeakDigestVerdict verdict) {
    return new RuleMatch(
        site.span(),
        message,
        List.of(),
        verdict.confidence(),
        verdict.assumptions()
    );
  }

  private static List<FlowSite> calls(final List<ControlFlowGraph> graphs) {
    return graphs.stream()
        .flatMap(graph -> graph.blocks().stream())
        .map(BasicBlock::sites)
        .flatMap(List::stream)
        .filter(site -> SPEC.sources().stream().anyMatch(entry -> entry.matches(site)))
        .toList();
  }
}
