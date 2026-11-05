package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the variable accesses the TypeScript and JavaScript lowerings record on each block. */
class NodeVariableAccessTest {

  @Test
  void selfAssignmentReadsBeforeItWrites() {
    final String source = "function f() { let x = 1; x = x + 1; }";

    assertThat(NodeAccessFixtures.kinds(NodeAccessFixtures.typescript(source, "f")))
        .containsExactly("DECLARE x", "WRITE x", "READ x", "WRITE x");
  }

  @Test
  void compoundAssignmentReadsThenWrites() {
    final String source = "function f(p) { let x = 0; x += p; }";

    assertThat(NodeAccessFixtures.kinds(NodeAccessFixtures.typescript(source, "f")))
        .containsExactly("PARAMETER p", "DECLARE x", "WRITE x", "READ x", "READ p", "WRITE x");
  }

  @Test
  void nestedArrowFunctionCapturesTheOuterVariableAtItsPosition() {
    final String source = "function f() { let total = 1; const g = () => total; }";
    final VariableAccess capture =
        NodeAccessFixtures.accessesOf(NodeAccessFixtures.typescript(source, "f")).stream()
            .filter(access -> access.kind() == AccessKind.CAPTURE)
            .findFirst()
            .orElseThrow();

    assertThat(capture.name()).isEqualTo("total");
    assertThat(source.substring(capture.span().startOffset(), capture.span().endOffset()))
        .startsWith("() => total");
  }

  @Test
  void typeScriptCallStatementReadsItsArgumentAndNeverWritesTheCallee() {
    final String source = "function f(item) { work(item); }";
    final List<VariableAccess> accesses =
        NodeAccessFixtures.accessesOf(NodeAccessFixtures.typescript(source, "f"));

    assertThat(NodeAccessFixtures.kinds(accesses)).containsExactly("PARAMETER item", "READ item");
    assertThat(accesses).noneMatch(access -> "work".equals(access.name()));
  }

  @Test
  void destructuringDeclaresEachBoundNameAfterItsInitializer() {
    final String source = "function f(p) { const { b, c: d } = p; use(b, d); }";

    assertThat(NodeAccessFixtures.kinds(NodeAccessFixtures.typescript(source, "f")))
        .containsExactly(
            "PARAMETER p",
            "READ p",
            "DECLARE b",
            "WRITE b",
            "DECLARE d",
            "WRITE d",
            "READ b",
            "READ d");
  }

  @Test
  void memberWritesAreNotRecorded() {
    final String source = "function f() { this.count = 1; obj.field = 2; }";

    assertThat(NodeAccessFixtures.accessesOf(NodeAccessFixtures.typescript(source, "f"))).isEmpty();
  }

  @Test
  void javaScriptForOfBindingIsDeclaredAndRead() {
    final String source = "function f(items) { for (const item of items) { work(item); } }";

    assertThat(NodeAccessFixtures.kinds(NodeAccessFixtures.javaScript(source, "f")))
        .contains("DECLARE item", "READ item");
  }

  @Test
  void javaScriptReadsAndWritesLocals() {
    final String source = "function f(n) { let i = 0; while (i < n) { i++; } }";

    assertThat(NodeAccessFixtures.kinds(NodeAccessFixtures.javaScript(source, "f")))
        .containsExactly(
            "PARAMETER n", "DECLARE i", "WRITE i", "READ i", "READ n", "READ i", "WRITE i");
  }
}
