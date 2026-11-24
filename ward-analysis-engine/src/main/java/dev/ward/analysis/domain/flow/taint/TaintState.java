package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.flow.AccessSite;
import dev.ward.analysis.domain.flow.ReachingDefinitions;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.Map;

/**
 * What the analysis of one graph knows: the graph, its reaching definitions and the taint of each
 * tainted write.
 *
 * @param graph the control-flow graph
 * @param reaching the reaching definitions of the graph
 * @param origins the taint of each tainted write
 */
record TaintState(
    ControlFlowGraph graph,
    ReachingDefinitions reaching,
    Map<AccessSite,
    Taint> origins
) {}
