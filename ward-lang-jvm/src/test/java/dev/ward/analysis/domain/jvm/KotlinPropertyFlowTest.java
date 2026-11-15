package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinTaintFixtures.findingsIn;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Property syntax for Java getters, and the initializers and accessors of properties. */
class KotlinPropertyFlowTest {

  private static List<String> bodies(final String source) {
    return KotlinFlowFixtures.lower(source).graphs().stream().map(ControlFlowGraph::body).toList();
  }

  @Test
  void aPropertyInitializerIsABodyOfItsOwn() {
    final String source = "class C {\n  val r = sink(src())\n}\n";

    assertThat(bodies(source)).contains("C#<prop:r>(0)");
    assertThat(findingsIn(source, "C#<prop:r>(0)")).hasSize(1);
  }

  @Test
  void aDelegateIsABodyAndItsLambdaIsLoweredInIt() {
    final String source = "class C {\n  val r by lazy { sink(src()) }\n}\n";

    assertThat(findingsIn(source, "C#<prop:r>(0)")).hasSize(1);
  }

  @Test
  void aTopLevelPropertyIsABodyWithNoOwner() {
    final String source = "val top = sink(src())\n";

    assertThat(bodies(source)).contains("#<prop:top>(0)");
    assertThat(findingsIn(source, "#<prop:top>(0)")).hasSize(1);
  }

  @Test
  void aGetterIsABodyWhetherItIsAnExpressionOrABlock() {
    final String source =
        "class C {\n  val a: String get() = sink(src())\n"
            + "  val b: String\n    get() {\n      sink(src())\n      return \"x\"\n    }\n}\n";

    assertThat(findingsIn(source, "C#<get:a>(0)")).hasSize(1);
    assertThat(findingsIn(source, "C#<get:b>(0)")).hasSize(1);
  }

  @Test
  void aSetterIsABody() {
    final String source =
        "class C {\n  var a: String = \"\"\n    set(value) {\n      sink(src())\n      field = value\n    }\n}\n";

    assertThat(findingsIn(source, "C#<set:a>(0)")).hasSize(1);
  }

  @Test
  void aLocalPropertyIsNotABodyOfItsOwn() {
    assertThat(bodies("class C {\n  fun f() {\n    val local = src()\n  }\n}\n"))
        .containsExactly("C#f(0)");
  }

  @Test
  void aPropertyInsideALocalClassIsABody() {
    final String source =
        "class C {\n  fun f() {\n    class Local {\n      val p = sink(src())\n    }\n  }\n}\n";

    assertThat(bodies(source)).contains("C.Local#<prop:p>(0)");
  }
}
