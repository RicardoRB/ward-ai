package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.KotlinCheckRuns.messages;
import static dev.ward.analysis.domain.rule.check.KotlinCheckRuns.positions;
import static dev.ward.analysis.domain.rule.check.KotlinCheckRuns.run;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin KDoc checks over real Kotlin source. */
class KotlinKdocChecksTest {

  private static final String MESSAGE = "m";

  @Test
  void kdocPositionReportsACommentBeforeAStatementOnly() {
    final String source =
        """
        /** File header. */
        package a.b

        /** A class. */
        class Documented {
            /** A member. */
            fun run() {
                /** Not a declaration. */
                println("x")
                /** A local. */
                val local = 1
            }
        }
        """;

    final List<RuleMatch> matches = run(new KotlinKdocPositionCheck(MESSAGE), source);

    assertThat(positions(matches)).containsExactly("8:9");
  }

  @Test
  void missingKdocTypeReportsPublicTypesWithoutAComment() {
    final String source =
        """
        class Undocumented {}

        /** Documented. */
        interface Documented

        private class Hidden {}
        """;

    final List<RuleMatch> matches = run(new KotlinMissingKdocTypeCheck(MESSAGE), source);

    assertThat(positions(matches)).containsExactly("1:1");
  }

  @Test
  void kdocMethodReportsMissingParamAndReturnTags() {
    final String source =
        """
        /**
         * Adds.
         * @param a the first
         */
        fun add(a: Int, b: Int): Int = a + b

        /**
         * Shows.
         * @param value the value
         */
        fun show(value: Int) {}

        /** Unit function with no tags. */
        fun nothing() {}
        """;

    final List<RuleMatch> matches = run(new KotlinKdocMethodCheck("{value}"), source);

    assertThat(messages(matches)).containsExactly("@param b", "@return");
    assertThat(positions(matches)).containsExactly("1:1", "1:1");
  }

  @Test
  void kdocMethodAcceptsParamsOfAnUndocumentedFunction() {
    final String source = "fun add(a: Int): Int = a\n";

    assertThat(run(new KotlinKdocMethodCheck("{value}"), source)).isEmpty();
  }
}
