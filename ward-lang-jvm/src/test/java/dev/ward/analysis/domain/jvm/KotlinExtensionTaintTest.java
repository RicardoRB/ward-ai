package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintFile;
import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Taint crosses an extension function: its receiver is the first argument and {@code this}. */
class KotlinExtensionTaintTest {
  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(entry("src", List.of())),
          List.of(entry("sink", List.of(0))),
          List.of(entry("clean", List.of())),
          List.of()
      );

  private static TaintEntry entry(final String name, final List<Integer> arguments) {
    return new TaintEntry(FlowSiteKind.CALL, name, Optional.empty(), Optional.empty(), arguments);
  }

  private static int count(final String source) {
    final List<ControlFlowGraph> graphs = KotlinFlowFixtures.lower(source).graphs();
    return TaintFile.run(graphs, SPEC).size();
  }

  @Test
  void aReceiverThatReachesASinkThroughThisIsReported() {
    assertThat(count("fun String.show() { sink(this) }\nfun f() { val x = src()\n x.show() }\n"))
        .isEqualTo(1);
  }

  @Test
  void aReceiverThatReachesASinkThroughAnImplicitCallIsReported() {
    assertThat(count("fun String.show() { sink(trim()) }\nfun f() { val x = src()\n x.show() }\n"))
        .isEqualTo(1);
  }

  @Test
  void anArgumentOfAnExtensionIsFollowedAsWell() {
    assertThat(
        count(
            "fun String.show(other: String) { sink(other) }\n"
                + "fun f() { \"a\".show(src()) }\n"
        )
    )
        .isEqualTo(1);
  }

  @Test
  void aReceiverThatIsReturnedIsASource() {
    assertThat(count("fun String.same(): String = this\nfun f() { sink(src().same()) }\n"))
        .isEqualTo(1);
  }

  @Test
  void aConstantReceiverIsNotReported() {
    assertThat(count("fun String.show() { sink(this) }\nfun f() { \"a\".show() }\n")).isZero();
  }

  @Test
  void aSanitisedReceiverIsNotReported() {
    assertThat(
        count("fun String.show() { sink(clean(this)) }\nfun f() { val x = src()\n x.show() }\n")
    )
        .isZero();
  }

  @Test
  void aMemberOfAnotherTypeWithTheSameNameIsNotAnExtensionCall() {
    assertThat(
        count(
            "fun String.show() { sink(this) }\n"
                + "class K { fun show(a: String) {} }\n"
                + "fun f(k: K) { k.show(src()) }\n"
        )
    )
        .isZero();
  }
}
