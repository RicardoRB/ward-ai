package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.KotlinCheckRuns.messages;
import static dev.ward.analysis.domain.rule.check.KotlinCheckRuns.positions;
import static dev.ward.analysis.domain.rule.check.KotlinCheckRuns.run;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin code-style checks over real Kotlin source. */
class KotlinStyleChecksTest {

  private static final String MESSAGE = "m";

  @Test
  void magicNumberSkipsIgnoredNumbersConstantsAnnotationsAndHashCode() {
    final String source =
        """
        @Suppress("42")
        class Values {
            const val KEPT = 9
            val zero = 0
            val one = 1
            val two = 2
            val minusOne = -1
            val magic = 42
            val hex = 0x1F
            val binary = 0b11
            val long = 5L
            val underscored = 1_000
            val real = 2.5f
            val negative = -7
            val folded = -(2)
            override fun hashCode(): Int = 17
        }
        """;

    final List<RuleMatch> matches = run(new KotlinMagicNumberCheck(MESSAGE), source);

    assertThat(messages(matches)).containsExactly("m", "m", "m", "m", "m", "m", "m");
  }

  @Test
  void magicNumberReportsTheLiteralAndItsSign() {
    final String source = "val a = -7\nval b = 2\nval c = 0b11\n";

    final List<RuleMatch> matches = run(new KotlinMagicNumberCheck(MESSAGE), source);

    assertThat(positions(matches)).containsExactly("1:10", "3:9");
  }

  @Test
  void magicNumberSkipsAnUnknownLiteralForm() {
    assertThat(run(new KotlinMagicNumberCheck(MESSAGE), "val a = 0x\n")).isEmpty();
  }

  @Test
  void hiddenFieldReportsParametersAndLocalsNamedAsProperties() {
    final String source =
        """
        class Account {
            private val balance = 0
            fun deposit(balance: Int) {
                val balance = 1
                println(balance)
            }
            fun reset(count: Int) {
                println(count)
            }
        }
        """;

    final List<RuleMatch> matches = run(new KotlinHiddenFieldCheck(MESSAGE), source);

    assertThat(positions(matches)).containsExactly("3:17", "4:13");
  }

  @Test
  void hiddenFieldIgnoresClassesWithoutProperties() {
    final String source = "class Plain {\n    fun run(name: String) {}\n}\n";

    assertThat(run(new KotlinHiddenFieldCheck(MESSAGE), source)).isEmpty();
  }

  @Test
  void constructorGroupingReportsAConstructorAfterAnotherMember() {
    final String source =
        """
        class Pair(val a: Int) {
            constructor() : this(0)
            fun sum(): Int = a
            constructor(b: Int, c: Int) : this(b + c)
        }
        """;

    final List<RuleMatch> matches = run(new KotlinConstructorGroupingCheck(MESSAGE), source);

    assertThat(positions(matches)).containsExactly("4:5");
  }

  @Test
  void oneStatementPerLineReportsASecondStatementOnALine() {
    final String source = "fun run() {\n    val a = 1; val b = 2\n    val c = 3\n}\n";

    final List<RuleMatch> matches = run(new KotlinOneStatementPerLineCheck(MESSAGE), source);

    assertThat(positions(matches)).containsExactly("2:16");
  }

  @Test
  void overloadOrderReportsAnOverloadSeparatedFromItsName() {
    final String source =
        """
        class Printer {
            fun print(text: String) {}
            fun print(number: Int) {}
            fun flush() {}
            fun print(flag: Boolean) {}
            fun print(text: String, count: Int) {}
        }
        """;

    final List<RuleMatch> matches = run(new KotlinOverloadOrderCheck(MESSAGE), source);

    assertThat(positions(matches)).containsExactly("5:5");
  }

  @Test
  void usageDistanceReportsAVarFirstUsedMoreThanThreeStatementsLater() {
    final String source =
        """
        fun total(values: List<Int>): Int {
            var sum = 0
            val a = 1
            val b = 2
            val c = 3
            val d = 4
            return sum + a + b + c + d
        }
        fun near(): Int {
            var count = 0
            count += 1
            return count
        }
        fun nested(flag: Boolean): Int {
            var inner = 0
            val a = 1
            val b = 2
            val c = 3
            if (flag) { inner = a + b + c }
            return inner
        }
        """;

    final List<RuleMatch> matches = run(new KotlinUsageDistanceCheck(MESSAGE), source);

    assertThat(positions(matches)).containsExactly("2:9");
  }
}
