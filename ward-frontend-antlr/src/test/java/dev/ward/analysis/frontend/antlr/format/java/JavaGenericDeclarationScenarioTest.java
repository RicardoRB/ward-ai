package dev.ward.analysis.frontend.antlr.format.java;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaGenericDeclarationScenarioTest {
  private static final String SOURCE =
      "interface Codec {\n"
          + "    /** Reads a value. */\n"
          + "    <T> T fromJson(String json, Class<T> type);\n"
          + "}\n";

  @Test
  @DisplayName("a generic method declaration")
  void aGenericMethodDeclaration() {
    // Given an interface member that starts with type parameters, after a javadoc
    final JavaFormatter formatter = new JavaFormatter();

    // When ward format runs
    final FormatResult result = formatter.format(
        SOURCE,
        FormatOptions.defaults(formatter.options())
    );

    // Then the declaration keeps the member indent, and --check reports nothing
    assertThat(result).isInstanceOf(FormatResult.Unchanged.class);
  }
}
