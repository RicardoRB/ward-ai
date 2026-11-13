package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintFinding;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaTaintAcrossMethodsTest {

  private static List<TaintFinding> run(final String members) {
    return JavaTaintFixtures.runFile("class Flow {\n" + members + "\n}\n");
  }

  @Test
  void sourceReturnedByAnotherMethodReachesTheSinkOfTheCaller() {
    final List<TaintFinding> found =
        run("String read() { return source(); }\nvoid run() { String d = read(); sink(d); }");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).sink().name()).isEqualTo("sink");
  }

  @Test
  void sourceReturnedThroughTwoMethodsIsFollowed() {
    final List<TaintFinding> found =
        run(
            "String a() { return source(); }\nString b() { String x = a(); return x; }\n"
                + "void run() { sink(this.b()); }");

    assertThat(found).hasSize(1);
  }

  @Test
  void parameterThatReachesASinkMakesTheCallASinkForThatArgument() {
    final List<TaintFinding> found =
        run("void put(String v) { sink(v); }\nvoid run() { String d = source(); put(d); }");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).sink().name()).isEqualTo("put");
  }

  @Test
  void constantPassedToASinkingMethodIsNotReported() {
    assertThat(run("void put(String v) { sink(v); }\nvoid run() { put(\"x\"); }")).isEmpty();
  }

  @Test
  void methodThatReturnsAConstantIsNotASource() {
    assertThat(run("String read() { return \"x\"; }\nvoid run() { sink(read()); }")).isEmpty();
  }

  @Test
  void sanitizedValueIsNotReportedAcrossTheCall() {
    assertThat(run("void put(String v) { sink(v); }\nvoid run() { put(clean(source())); }"))
        .isEmpty();
  }

  @Test
  void overloadsWithOneArityAreNotFollowed() {
    assertThat(
            run(
                "String read() { return source(); }\nclass Other { String read() { return \"\"; } }\n"
                    + "void run() { sink(read()); }"))
        .isEmpty();
  }

  @Test
  void recursionTerminates() {
    assertThat(run("void loop(String v) { loop(v); sink(v); }\nvoid run() { loop(source()); }"))
        .hasSize(1);
  }
}
