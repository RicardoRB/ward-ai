package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExcludeGlobTest {

  @Test
  void doubleStarCrossesDirectoriesAndSingleStarDoesNot() {
    assertThat(ExcludeGlob.compile("src/generated/**").matcher("src/generated/a/B.java").matches())
        .isTrue();
    assertThat(ExcludeGlob.compile("src/*.java").matcher("src/a/B.java").matches()).isFalse();
    assertThat(ExcludeGlob.compile("src/*.java").matcher("src/B.java").matches()).isTrue();
  }

  @Test
  void bracesAndQuestionMarkMatch() {
    assertThat(ExcludeGlob.compile("**/*.{kt,java}").matcher("a/B.kt").matches()).isTrue();
    assertThat(ExcludeGlob.compile("a?.js").matcher("ab.js").matches()).isTrue();
    assertThat(ExcludeGlob.compile("a.b").matcher("axb").matches()).isFalse();
  }

  @Test
  void invalidGlobsAreRejected() {
    assertThatThrownBy(() -> ExcludeGlob.compile("[a"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExcludeGlob.compile("{a"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExcludeGlob.compile("a}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
