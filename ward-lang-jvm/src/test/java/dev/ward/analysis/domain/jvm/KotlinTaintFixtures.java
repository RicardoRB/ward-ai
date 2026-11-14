package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.flow.taint.TaintAnalysis;
import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;

/**
 * Runs the taint analysis on a Kotlin function with a tiny specification: {@code src()} is a
 * source, {@code sink(x)} guards its argument, and {@code clean(x)} is a sanitizer.
 */
final class KotlinTaintFixtures {

  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(entry("src", List.of())),
          List.of(entry("sink", List.of(0))),
          List.of(entry("clean", List.of())),
          List.of());

  private KotlinTaintFixtures() {}

  private static TaintEntry entry(final String name, final List<Integer> arguments) {
    return new TaintEntry(FlowSiteKind.CALL, name, Optional.empty(), Optional.empty(), arguments);
  }

  /** The findings in the body of {@code fun f(a: String)} of class C, written as {@code body}. */
  static List<TaintFinding> findings(final String body) {
    return TaintAnalysis.run(
        KotlinFlowFixtures.graph("class C {\nfun f(a: String) {\n" + body + "\n}\n}\n", "C#f(1)"),
        SPEC);
  }

  /** The number of findings of the body. */
  static int count(final String body) {
    return findings(body).size();
  }
}
