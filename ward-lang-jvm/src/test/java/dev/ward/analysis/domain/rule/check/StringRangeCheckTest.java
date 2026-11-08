package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StringRangeCheckTest {

  private static final StringRangeCheck CHECK = new StringRangeCheck("message");

  /** Runs the check; {@code indexes} are the texts of the constant integer arguments. */
  private static List<String> run(final String call, final String... indexes) {
    final ParsedFacts facts =
        new ParsedFacts(
                "final class C {\n  Object t(String s) {\n    return " + call + ";\n  }\n}\n")
            .at("expression", "\"abc\"", ExtraFacts.constant("abc"));
    for (final String index : indexes) {
      facts.at("expression", index, ExtraFacts.constant(Long.parseLong(index)));
    }
    return facts.run(CHECK);
  }

  @Test
  void anIndexOutsideAConstantStringIsReported() {
    assertThat(run("\"abc\".charAt(5)", "5")).containsExactly("3:12");
    assertThat(run("\"abc\".charAt(-1)", "-1")).hasSize(1);
    assertThat(run("\"abc\".codePointAt(3)", "3")).hasSize(1);
    assertThat(run("\"abc\".substring(4)", "4")).hasSize(1);
    assertThat(run("\"abc\".substring(1, 9)", "1", "9")).hasSize(1);
    assertThat(run("\"abc\".substring(2, 1)", "2", "1")).hasSize(1);
  }

  @Test
  void anIndexInsideTheStringAnUnknownIndexOrAnotherMethodIsNotReported() {
    assertThat(run("\"abc\".charAt(2)", "2")).isEmpty();
    assertThat(run("\"abc\".substring(3)", "3")).isEmpty();
    assertThat(run("\"abc\".substring(0, 3)", "0", "3")).isEmpty();
    assertThat(run("\"abc\".charAt(i)")).isEmpty();
    assertThat(run("\"abc\".indexOf(5)", "5")).isEmpty();
    assertThat(run("\"abc\".substring(1, 2, 3)", "1", "2", "3")).isEmpty();
    assertThat(run("\"abc\".charAt(\"x\")")).isEmpty();
    assertThat(run("s.charAt(5)", "5")).isEmpty();
    assertThat(new ParsedFacts("class C {}").run(CHECK)).isEmpty();
    assertThat(new ParsedFacts("class C {}").plain(CHECK)).isEmpty();
  }
}
