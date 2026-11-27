package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;

/** Tells when a null check in a graph may need the files that depend on it to be proved. */
public final class MemberVouching {
  private MemberVouching() {}

  /** Whether a graph has a null check that the files that depend on it may be needed to prove. */
  public static boolean mayVouch(final List<ControlFlowGraph> graphs) {
    return graphs.stream().anyMatch(MemberLookups::marked);
  }
}
