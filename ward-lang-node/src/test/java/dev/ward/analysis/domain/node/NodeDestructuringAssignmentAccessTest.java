package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import org.junit.jupiter.api.Test;

/**
 * Checks the accesses of destructuring assignments, where the pattern's names are written after the
 * value is read, in both TypeScript and JavaScript. A write that is recorded as a read would hide a
 * dead store or an unused assignment of the variable.
 */
class NodeDestructuringAssignmentAccessTest {

  private static final String WRITE_A = "WRITE a";
  private static final String READ_A = "READ a";
  private static final String READ_SRC = "READ src";

  private static void assertBothLanguages(final String source, final String... expected) {
    final ControlFlowGraph typescript = NodeAccessFixtures.typescript(source, "f");
    final ControlFlowGraph javascript = NodeAccessFixtures.javaScript(source, "f");

    assertThat(NodeAccessFixtures.kinds(typescript)).containsExactly(expected);
    assertThat(NodeAccessFixtures.kinds(javascript)).containsExactly(expected);
  }

  @Test
  void objectPatternAssignmentReadsTheValueThenWritesTheName() {
    assertBothLanguages(
        "function f(src) { let a = 1; ({ a } = src); return a; }",
        "PARAMETER src",
        "DECLARE a",
        WRITE_A,
        READ_SRC,
        WRITE_A,
        READ_A);
  }

  @Test
  void arrayPatternAssignmentReadsTheValueThenWritesTheName() {
    assertBothLanguages(
        "function f(src) { let a = 1; [a] = src; return a; }",
        "PARAMETER src",
        "DECLARE a",
        WRITE_A,
        READ_SRC,
        WRITE_A,
        READ_A);
  }

  @Test
  void aDefaultIsReadWhereThePatternReachesItBeforeTheNameIsWritten() {
    assertBothLanguages(
        "function f(src, dflt) { let a = 1; ({ a = dflt } = src); return a; }",
        "PARAMETER src",
        "PARAMETER dflt",
        "DECLARE a",
        WRITE_A,
        READ_SRC,
        "READ dflt",
        WRITE_A,
        READ_A);
  }

  @Test
  void nestedPatternWritesItsInnerNameAndReadsItsDefault() {
    assertBothLanguages(
        "function f(src, c) { let a = 1; ({ b: [a = c] } = src); return a; }",
        "PARAMETER src",
        "PARAMETER c",
        "DECLARE a",
        WRITE_A,
        READ_SRC,
        "READ c",
        WRITE_A,
        READ_A);
  }

  @Test
  void aMemberTargetInsideAPatternStillReadsItsObject() {
    assertBothLanguages(
        "function f(o, v) { ({ x: o.p } = v); }", "PARAMETER o", "PARAMETER v", "READ v", "READ o");
  }
}
