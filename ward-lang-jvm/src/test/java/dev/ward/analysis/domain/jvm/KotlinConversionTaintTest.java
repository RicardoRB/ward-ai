package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintFile;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A conversion written on the receiver, {@code value.toInt()}, cleans it like {@code toInt(value)}. */
class KotlinConversionTaintTest {
  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(entry("src", List.of())),
          List.of(entry("sink", List.of(0))),
          List.of(entry("toInt", List.of()), entry("toLongOrNull", List.of())),
          List.of()
      );

  private static TaintEntry entry(final String name, final List<Integer> arguments) {
    return new TaintEntry(FlowSiteKind.CALL, name, Optional.empty(), Optional.empty(), arguments);
  }

  private static int count(final String body) {
    final String source = "class C {\nfun f() {\n" + body + "\n}\n}\n";
    return TaintFile.run(KotlinFlowFixtures.lower(source).graphs(), SPEC).size();
  }

  @Test
  void aConversionOnTheReceiverCleansTheValue() {
    assertThat(count("sink(src().toInt())")).isZero();
    assertThat(
        count("val n = (src().toLongOrNull() ?: 0).coerceAtLeast(0)\nsink(\"/r/$n\")")
    ).isZero();
  }

  @Test
  void aTransformThatIsNotAConversionKeepsTheTaint() {
    assertThat(count("sink(src().trim())")).isEqualTo(1);
    assertThat(count("sink(\"/r/\" + src().toString())")).isEqualTo(1);
  }
}
