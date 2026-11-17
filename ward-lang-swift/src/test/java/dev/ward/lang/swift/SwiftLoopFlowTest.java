package dev.ward.lang.swift;

import static dev.ward.lang.swift.SwiftFlowFixtures.count;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Loops, switch, do-catch and jumps of the Swift lowering, read through the taint analysis. */
final class SwiftLoopFlowTest {
  @Test
  void aValueWrittenInARepeatLoopReachesAfterIt() {
    assertThat(count("var x = \"\"\nrepeat { x = src() } while x.isEmpty\nsink(x)")).isEqualTo(1);
  }

  @Test
  void theVariableOfAForLoopTakesTheTaintOfItsSequence() {
    assertThat(count("for item in [src()] { sink(item) }")).isEqualTo(1);
  }

  @Test
  void aLabelledLoopLeavesThroughItsBreak() {
    assertThat(
        count(
            "var x = \"\"\nouter: for i in [1] {\nfor j in [2] { if j > i { break outer }\n"
                + "continue outer }\nx = src() }\nsink(x)"
        )
    )
        .isEqualTo(1);
  }

  @Test
  void aValueFromOneCaseOfASwitchReaches() {
    assertThat(
        count(
            "var x = \"k\"\nswitch a {\ncase \"a\":\nx = src()\ncase \"b\":\nbreak\ndefault:\nx = \"d\"\n}\nsink(x)"
        )
    )
        .isEqualTo(1);
  }

  @Test
  void aSwitchWithoutDefaultKeepsTheValueBefore() {
    assertThat(
        count(
            "var x = src()\nswitch a {\ncase \"a\":\nx = \"k\"\ncase \"b\":\nx = \"j\"\n}\nsink(x)"
        )
    )
        .isEqualTo(1);
  }

  @Test
  void aValueWrittenInADoBodyReachesAfterTheCatch() {
    assertThat(
        count(
            "var x = \"\"\ndo {\nx = src()\ntry check()\n} catch let e as Failure {\nprint(e)\n} catch {\nprint(error)\n}\nsink(x)"
        )
    )
        .isEqualTo(1);
  }

  @Test
  void aThrowLeavesTheBody() {
    assertThat(count("if a.isEmpty { throw Failure() }\nsink(src())")).isEqualTo(1);
  }

  @Test
  void aReturnedValueIsReadAsTheValueOfTheBody() {
    final String source = "struct C {\nfunc make() -> String {\nreturn src()\n}\nfunc use() {\nsink(make())\n}\n}\n";

    assertThat(SwiftFlowFixtures.file(source)).hasSize(1);
  }

  @Test
  void aCallThroughSelfReachesTheMethodOfTheSameType() {
    final String source = "struct C {\nfunc show(_ v: String) {\nsink(v)\n}\nfunc use() {\nself.show(src())\n}\n}\n";

    assertThat(SwiftFlowFixtures.file(source)).hasSize(1);
  }
}
