package dev.ward.analysis.frontend.antlr;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeclarationTextTest {
  private static final String JAVA =
      """
      public class A {
        int[] a = {1, 2};
        Runnable r = () -> { run(); };
        static { init(); }
        public A() { this(1); }
        <T> T m(T t) throws Exception { return t; }
        interface V { default void v() { call(); } }
        enum E { X { void f() { keep(); } }, Y; int g() { return 1; } }
        record P(int x) { P { check(x); } }
        @interface T { String[] v() default {"a"}; }
        Object o = new Object() { void h() { keep(); } };
      }
      """;
  private static final String KOTLIN =
      """
      class A(val n: Int = 1) {
        val area: Int
          get() { return n }
        init { start() }
        constructor(s: String) : this(1) { use(s) }
        fun block(): Int { return n }
        fun expr() = run { n }
        val lazy by lazy { 1 }
        val text = "n ${n + 1}"
        enum class E { X { override fun toString() = "x" }, Y; fun g(): Int { return 1 } }
        companion object { fun make(): A { return A(1) } }
        val anon = object { fun f() { keep() } }
      }
      fun top(): Int { return 1 }
      """;

  private static String reduce(final AnalysisLanguage language, final String text) {
    return DeclarationText.of(new SourceUnit("A", language, text)).text();
  }

  @Test
  @DisplayName("the bodies of Java members are blanked")
  void javaBodiesAreBlanked() {
    // Given
    final AnalysisLanguage language = AnalysisLanguage.JAVA;

    // When
    final String reduced = reduce(language, JAVA);

    // Then
    assertThat(reduced).hasSameSizeAs(JAVA).hasLineCount(JAVA.lines().toList().size());
    assertThat(reduced)
        .doesNotContain("init()", "this(1)", "return t", "call()", "return 1", "check(x)")
        .contains("{1, 2}", "run();", "keep();", "{\"a\"}");
  }

  @Test
  @DisplayName("the bodies of Kotlin members are blanked and the typing ones kept")
  void kotlinBodiesAreBlanked() {
    // Given
    final AnalysisLanguage language = AnalysisLanguage.KOTLIN;

    // When
    final String reduced = reduce(language, KOTLIN);

    // Then
    assertThat(reduced).hasSameSizeAs(KOTLIN);
    assertThat(reduced)
        .doesNotContain("return n", "start()", "use(s)", "A(1) }", "return 1")
        .contains("run { n }", "lazy { 1 }", "${n + 1}", "keep()", "toString() = \"x\"");
  }

  @Test
  @DisplayName("a text that cannot be read safely is returned unchanged")
  void unsafeTextIsUnchanged() {
    // Given
    final String unbalanced = "class A { void f() { }";
    final String unlexable = "class A { void f() { } # }";
    final SourceUnit python = new SourceUnit("a.py", AnalysisLanguage.PYTHON, "def f(): {}");

    // When
    final SourceUnit other = DeclarationText.of(python);

    // Then
    assertThat(reduce(AnalysisLanguage.JAVA, unbalanced)).isEqualTo(unbalanced);
    assertThat(reduce(AnalysisLanguage.JAVA, unlexable)).isEqualTo(unlexable);
    assertThat(reduce(AnalysisLanguage.KOTLIN, "}")).isEqualTo("}");
    assertThat(other).isSameAs(python);
    assertThat(reduce(AnalysisLanguage.JAVA, "}")).isEqualTo("}");
  }
}
