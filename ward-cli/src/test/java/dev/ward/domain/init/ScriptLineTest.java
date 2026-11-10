package dev.ward.domain.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Argument vectors become package.json script text, quoting only what a shell would split. */
class ScriptLineTest {
  @Test
  void plainArgumentsStayBare() {
    assertThat(ScriptLine.of(List.of("tsc", "--noEmit", "--pretty", "false")))
        .isEqualTo("tsc --noEmit --pretty false");
    assertThat(ScriptLine.of(List.of("eslint", ".", "--format", "json")))
        .isEqualTo("eslint . --format json");
  }

  @Test
  void jestThresholdJsonIsQuotedWithEscapedQuotes() {
    assertThat(ScriptLine.of(List.of("jest", "--coverageThreshold", "{\"global\":{\"lines\":80}}")))
        .isEqualTo("jest --coverageThreshold \"{\\\"global\\\":{\\\"lines\\\":80}}\"");
  }

  @Test
  void dollarSignsAndBackticksAreEscapedInsideQuotes() {
    assertThat(ScriptLine.of(List.of("echo", "$HOME `pwd`")))
        .isEqualTo("echo \"\\$HOME \\`pwd\\`\"");
  }

  @Test
  void emptyArgumentIsQuoted() {
    assertThat(ScriptLine.of(List.of("npm", ""))).isEqualTo("npm \"\"");
  }
}
