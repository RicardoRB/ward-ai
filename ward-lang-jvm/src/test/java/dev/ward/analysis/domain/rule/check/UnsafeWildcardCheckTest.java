package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnsafeWildcardCheckTest {

  private static final UnsafeWildcardCheck CHECK = new UnsafeWildcardCheck("message");
  private static final String HEAD = "class G<T extends Number> {}\nclass C<U> {\n";

  private static List<String> run(final String body) {
    return new ParsedFacts(HEAD + body + "}\n")
        .plain(CHECK).stream()
            .map(match -> match.span().startLine() + ":" + match.span().startColumn())
            .toList();
  }

  @Test
  void aNullOrAnUninitializedFieldOfAnUnsafeWildcardTypeIsReported() {
    assertThat(run("  G<? super U> f = null;\n")).containsExactly("3:20");
    assertThat(run("  G<? super U> f;\n")).containsExactly("3:3");
    assertThat(run("  void m() {\n    G<? super U> v = null;\n  }\n")).containsExactly("4:22");
    assertThat(run("  G<? super U> m() {\n    return null;\n  }\n")).containsExactly("4:12");
    assertThat(run("  Object m() {\n    return (G<? super U>) null;\n  }\n"))
        .containsExactly("4:27");
    assertThat(run("  java.util.List<G<? super U>> f = null;\n")).hasSize(1);
  }

  @Test
  void safeWildcardsAndTypesOfOtherFilesAreNotReported() {
    assertThat(run("  G<? super Integer> f = null;\n")).isEmpty();
    assertThat(run("  G<? extends U> f = null;\n")).isEmpty();
    assertThat(run("  G<?> f = null;\n")).isEmpty();
    assertThat(run("  G<? super U> f = new G<>();\n")).isEmpty();
    assertThat(run("  final G<? super U> f;\n  C() {\n    f = null;\n  }\n")).hasSize(1);
    assertThat(run("  Other<? super U> f = null;\n")).isEmpty();
    assertThat(
            new ParsedFacts("class B<T> {}\nclass C<U> {\n  B<? super U> f = null;\n}\n")
                .plain(CHECK))
        .isEmpty();
    assertThat(
            new ParsedFacts(
                    "class G<T extends Number> {}\nclass C<U extends Number> {\n  G<? super U> f = null;\n}\n")
                .plain(CHECK))
        .isEmpty();
  }

  @Test
  void anUnsafeWildcardInASupertypeIsReported() {
    final String source =
        "interface G<T extends Number> {}\nclass C<U> implements G<? super U> {}\n";
    assertThat(new ParsedFacts(source).plain(CHECK)).hasSize(1);
  }
}
