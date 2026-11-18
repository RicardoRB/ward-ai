package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import java.util.ArrayList;
import java.util.List;

/** The taint summary of a file, see {@link #of}. */
public final class TaintFileSummary {
  private TaintFileSummary() {}

  /**
   * The taint summary of a file given the files it depends on: the sources and sinks that the
   * methods its calls reach add to the specification, that is what each takes, returns and passes
   * to a sink, and the owner that each of its calls on a typed receiver was resolved to. {@link
   * #run(List, List, TaintSpec)} reads the other files only through these, so two runs of one file
   * with equal summaries give equal findings, whatever the text of the other files.
   *
   * @param graphs the control-flow graphs of the file, in source order
   * @param related the graphs of the files it depends on, directly or not
   * @param spec the taint specification
   * @return the summary as text, equal for equal summaries
   */
  public static String of(
      final List<ControlFlowGraph> graphs,
      final List<ControlFlowGraph> related,
      final TaintSpec spec
  ) {
    return of(graphs, related, List.of(spec));
  }

  /**
   * The summaries of a file for each of the specifications, one after the other, with what does
   * not depend on a specification read once.
   *
   * @param graphs the control-flow graphs of the file, in source order
   * @param related the graphs of the files it depends on, directly or not
   * @param specs the taint specifications
   * @return the summaries as text, equal for equal summaries
   */
  public static String of(
      final List<ControlFlowGraph> graphs,
      final List<ControlFlowGraph> related,
      final List<TaintSpec> specs
  ) {
    final TaintContext context = TaintContext.of(graphs, related);
    final List<String> summaries = new ArrayList<>();
    for (final TaintSpec spec : specs) {
      summaries.add(summary(context, graphs, spec));
    }
    return String.join("\n--\n", summaries);
  }

  private static String summary(
      final TaintContext context,
      final List<ControlFlowGraph> graphs,
      final TaintSpec spec
  ) {
    final TaintFile.Prepared prepared = TaintFile.prepare(context, graphs, spec);
    final List<String> lines = new ArrayList<>();
    prepared.effective().sources().stream()
        .skip(spec.sources().size())
        .forEach(entry -> lines.add("source " + entry));
    prepared.effective().sinks().stream()
        .skip(spec.sinks().size())
        .forEach(entry -> lines.add("sink " + entry));
    lines.sort(null);
    for (int index = 0; index < graphs.size(); index++) {
      resolvedCalls(index, graphs.get(index), prepared.own().get(index), lines);
    }
    return String.join("\n", lines);
  }

  private static void resolvedCalls(
      final int index,
      final ControlFlowGraph before,
      final ControlFlowGraph after,
      final List<String> lines
  ) {
    for (int block = 0; block < before.blocks().size(); block++) {
      final List<FlowSite> was = before.blocks().get(block).sites();
      final List<FlowSite> now = after.blocks().get(block).sites();
      for (int site = 0; site < was.size(); site++) {
        if (!was.get(site).equals(now.get(site))) {
          lines.add("call " + index + "." + block + "." + site + " " + now.get(site).qualifier());
        }
      }
    }
  }
}
