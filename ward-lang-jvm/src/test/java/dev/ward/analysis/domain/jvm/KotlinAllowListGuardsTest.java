package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintFile;
import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A Kotlin value checked against an allow-list of constants is clean past the check. */
class KotlinAllowListGuardsTest {
  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(entry("src", List.of())),
          List.of(entry("sink", List.of(0))),
          List.of(),
          List.of()
      );
  private static final String LIST = "val ALLOWED = setOf(\"a\", \"b\")\n";

  private static TaintEntry entry(final String name, final List<Integer> arguments) {
    return new TaintEntry(FlowSiteKind.CALL, name, Optional.empty(), Optional.empty(), arguments);
  }

  private static int count(final String declarations, final String body) {
    final String source = declarations + "class C {\nfun f() {\nval x = src()\n" + body + "\n}\n}\n";
    return TaintFile.run(KotlinFlowFixtures.lower(source).graphs(), SPEC).size();
  }

  @Test
  void aNegatedMembershipThatThrowsCleansTheValue() {
    assertThat(count(LIST, "if (x !in ALLOWED) throw IllegalStateException()\nsink(x)")).isZero();
    assertThat(count(LIST, "if (!ALLOWED.contains(x)) { return }\nsink(x)")).isZero();
    assertThat(count(LIST, "if (!(x in ALLOWED)) { println(1)\n throw Exception() }\nsink(x)"))
        .isZero();
  }

  @Test
  void aParenthesisAroundTheWholeConditionIsIgnored() {
    assertThat(count(LIST, "if ((x !in ALLOWED)) throw Exception()\nsink(x)")).isZero();
    assertThat(count(LIST, "if ((x !in ALLOWED) || flag()) throw Exception()\nsink(x)"))
        .isEqualTo(1);
    assertThat(count(LIST, "if (flag) throw Exception()\nsink(x)")).isEqualTo(1);
  }

  @Test
  void aModifierOnTheAllowListDoesNotMatter() {
    assertThat(
        count(
            "private val ALLOWED = setOf(\"a\")\n",
            "if (x !in ALLOWED) throw IllegalStateException()\nsink(x)"
        )
    )
        .isZero();
  }

  @Test
  void requireAndCheckCleanTheValue() {
    assertThat(count(LIST, "require(x in ALLOWED)\nsink(x)")).isZero();
    assertThat(count(LIST, "check(ALLOWED.contains(x))\nsink(x)")).isZero();
    assertThat(count(LIST, "require(x in ALLOWED) { \"no\" }\nsink(x)")).isZero();
  }

  @Test
  void aCheckWithoutAnExitOrOfAnotherCollectionLeavesTheValueTainted() {
    assertThat(count(LIST, "if (x !in ALLOWED) println(1)\nsink(x)")).isEqualTo(1);
    assertThat(
        count("val OTHER = mutableSetOf(\"a\")\n", "if (x !in OTHER) throw Exception()\nsink(x)")
    )
        .isEqualTo(1);
    assertThat(count(LIST, "if (x in ALLOWED) throw Exception()\nsink(x)")).isEqualTo(1);
    assertThat(count(LIST, "if (x !in ALLOWED) throw Exception() else println(1)\nsink(x)"))
        .isEqualTo(1);
  }
}
