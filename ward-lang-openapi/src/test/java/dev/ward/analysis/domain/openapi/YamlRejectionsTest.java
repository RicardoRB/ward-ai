package dev.ward.analysis.domain.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class YamlRejectionsTest {

  private static final int BOMB_LEVELS = 25;
  private static final int DEEP = 5000;

  @Test
  void rejectsTabIndentation() {
    assertThat(YamlTrees.dump("a:\n\tb: 1\n"))
        .isEqualTo("ERROR 2:1 tab characters cannot indent a line");
  }

  @Test
  void rejectsEmptyAndCommentOnlyInput() {
    assertThat(YamlTrees.dump("")).isEqualTo("ERROR 1:1 the document is empty");
    assertThat(YamlTrees.dump("# nothing\n\n")).isEqualTo("ERROR 1:1 the document is empty");
  }

  @Test
  void rejectsBinaryInput() {
    assertThat(YamlTrees.dump("ab\0cd")).isEqualTo("ERROR 1:3 the file is binary, not text");
  }

  @Test
  void rejectsAnUnterminatedString() {
    assertThat(YamlTrees.dump("a: \"open\nb: 1\n")).startsWith("ERROR 1:4 unterminated");
    assertThat(YamlTrees.dump("a: 'open")).isEqualTo("ERROR 1:4 unterminated single-quoted string");
  }

  @Test
  void rejectsWhatItDoesNotRead() {
    assertThat(YamlTrees.dump("a: !!str 1\n")).isEqualTo("ERROR 1:4 tags are not supported");
    assertThat(YamlTrees.dump("? a\n: b\n"))
        .isEqualTo("ERROR 1:1 complex mapping keys are not supported");
    assertThat(YamlTrees.dump("%YAML 1.2\n---\na: 1\n"))
        .isEqualTo("ERROR 1:1 YAML directives are not supported");
    assertThat(YamlTrees.dump("a: *missing\n"))
        .isEqualTo("ERROR 1:4 the alias 'missing' has no anchor before it");
  }

  @Test
  void anAliasBombIsRejected() {
    final String bomb =
        "l0: &l0 [x, x]\n"
            + IntStream.range(1, BOMB_LEVELS)
                .mapToObj(
                    level -> "l%d: &l%d [*l%d, *l%d]".formatted(level, level, level - 1, level - 1))
                .collect(Collectors.joining("\n"));

    assertThat(YamlTrees.dump(bomb)).contains("too many nodes");
  }

  @Test
  void deepNestingIsRejectedNotAStackOverflow() {
    assertThat(YamlTrees.dump("[".repeat(DEEP) + "]".repeat(DEEP))).contains("nested too deeply");
  }
}
