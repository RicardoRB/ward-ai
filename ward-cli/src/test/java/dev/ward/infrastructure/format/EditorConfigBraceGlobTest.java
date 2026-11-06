package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EditorConfigBraceGlobTest {

  @Test
  void bracesMatchAnyListedAlternative() {
    final Pattern pattern = EditorConfigGlob.compile("*.{ts,tsx}");
    assertThat(pattern.matcher("App.ts").matches()).isTrue();
    assertThat(pattern.matcher("App.tsx").matches()).isTrue();
    assertThat(pattern.matcher("App.js").matches()).isFalse();
  }

  @Test
  void nestedBracesMatchAlternativesInsideAlternatives() {
    final Pattern pattern = EditorConfigGlob.compile("*.{a,{b,c}}");
    assertThat(pattern.matcher("x.a").matches()).isTrue();
    assertThat(pattern.matcher("x.c").matches()).isTrue();
    assertThat(pattern.matcher("x.d").matches()).isFalse();
  }

  @Test
  void numericRangeMatchesEveryNumberInclusive() {
    final Pattern pattern = EditorConfigGlob.compile("file{1..3}.txt");
    assertThat(pattern.matcher("file1.txt").matches()).isTrue();
    assertThat(pattern.matcher("file3.txt").matches()).isTrue();
    assertThat(pattern.matcher("file4.txt").matches()).isFalse();
    assertThat(EditorConfigGlob.compile("f{3..1}").matcher("f2").matches()).isTrue();
  }

  @Test
  void braceWithoutCommaOrRangeIsLiteral() {
    assertThat(EditorConfigGlob.compile("{single}.txt").matcher("{single}.txt").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("{single}.txt").matcher("single.txt").matches()).isFalse();
  }

  @Test
  void unclosedBracketAndBraceAreLiterals() {
    assertThat(EditorConfigGlob.compile("a[b.java").matcher("a[b.java").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("{a,b.java").matcher("{a,b.java").matches()).isTrue();
  }

  @Test
  void escapedCharacterIsLiteralAndTrailingBackslashIsLiteral() {
    assertThat(EditorConfigGlob.compile("a\\*b").matcher("a*b").matches()).isTrue();
    assertThat(EditorConfigGlob.compile("a\\*b").matcher("aXb").matches()).isFalse();
    assertThat(EditorConfigGlob.compile("a\\").matcher("a\\").matches()).isTrue();
  }
}
