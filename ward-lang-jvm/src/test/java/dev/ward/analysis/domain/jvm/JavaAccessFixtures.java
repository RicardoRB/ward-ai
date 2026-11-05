package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.port.ControlFlowResult;
import java.util.List;

/** Looks up the blocks and accesses of a lowered Java body for the access tests. */
final class JavaAccessFixtures {

  private JavaAccessFixtures() {}

  static List<BasicBlock> accessesOf(final String source, final String body) {
    return graphNamed(source, body).blocks();
  }

  static List<String> kinds(final List<BasicBlock> blocks, final int block) {
    return kinds(List.of(blocks.get(block)));
  }

  static List<String> kinds(final List<BasicBlock> blocks) {
    return blocks.stream()
        .flatMap(block -> block.accesses().stream())
        .map(access -> access.kind() + " " + access.name())
        .toList();
  }

  static ControlFlowGraph graphNamed(final String source, final String prefix) {
    final ControlFlowResult result = JavaFlowFixtures.lower(source);
    return result.graphs().stream()
        .filter(graph -> graph.body().startsWith(prefix))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError("no graph " + prefix + " in " + JavaFlowFixtures.names(result)));
  }

  static List<VariableAccess> allAccesses(final ControlFlowGraph graph) {
    return graph.blocks().stream().flatMap(block -> block.accesses().stream()).toList();
  }
}
