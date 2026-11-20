package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Checks which replace chains remove every line break of a String receiver. */
class JavaLineBreakStripsTest {
  private static final String USER = "user";

  private static boolean strip(
      final String name,
      final String target,
      final String replacement,
      final String qualifier
  ) {
    return JavaLineBreakStrips.removesAll(
        name,
        List.of(target, replacement),
        qualifier,
        text -> USER.equals(text) ? Optional.of("java.lang.String") : Optional.empty()
    );
  }

  /** Scenario: a chain that removes both line breaks is a sanitizer. */
  @Test
  void chainRemovingBothLineBreaksGuardsTheReceiver() {
    assertThat(strip("replace", "\"\\n\"", "\"_\"", "user.replace(\"\\r\",\"_\")")).isTrue();
  }

  /** Scenario: one call or chain that removes both line breaks, in each accepted form. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '`',
      value = {
          "replaceAll | \"[\\\\r\\\\n]\" | \"\"   | user",
          "replaceAll | \"\\\\R\"        | \"\\s\" | user",
          "replace    | '\\r'            | ' '    | user.replace('\\n',' ')",
          "replace    | \"\\r\"          | \"\"   | user.replaceAll(\"\\n+\",\"\")"
      }
  )
  void acceptedFormsAreSanitizers(
      final String name,
      final String target,
      final String replacement,
      final String qualifier
  ) {
    assertThat(strip(name, target.strip(), replacement.strip(), qualifier.strip())).isTrue();
  }

  /** Scenario: a chain that leaves a line break, adds one back or cannot be read is not one. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '`',
      value = {
          "replace    | \"\\n\"          | \"_\"    | user",
          "replace    | \"\\r\\n\"       | \"_\"    | user",
          "replace    | \"\\n\"          | \"\\r\"  | user.replace(\"\\r\",\"_\")",
          "replaceAll | \"([\\\\r\\\\n])\" | \"$1\" | user",
          "replaceAll | \"[\"            | \"\"     | user.replace(\"\\r\",\"_\")",
          "replace    | \"\\u000a\"      | \"\"     | user.replace(\"\\r\",\"_\")",
          "replace    | \"\\n\"          | sep      | user.replace(\"\\r\",\"_\")",
          "replace    | \"\\n\"          | \"_\"    | other.replace(\"\\r\",\"_\")",
          "concat     | \"\\n\"          | \"_\"    | user.replace(\"\\r\",\"_\")"
      }
  )
  void otherChainsAreNotSanitizers(
      final String name,
      final String target,
      final String replacement,
      final String qualifier
  ) {
    assertThat(strip(name, target.strip(), replacement.strip(), qualifier.strip())).isFalse();
  }

  /** Scenario: a call with another number of arguments is not a replace of a literal. */
  @Test
  void oneArgumentIsNotASanitizer() {
    assertThat(
        JavaLineBreakStrips.removesAll(
            "replace",
            List.of("\"\\n\""),
            USER,
            text -> Optional.of("java.lang.String")
        )
    )
        .isFalse();
  }
}
