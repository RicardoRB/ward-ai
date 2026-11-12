package dev.ward.application.gate.openapi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiCommandValidationTest {

  @Test
  void rejectsADiffCommandThatIsNotTheBreakingSubcommand() {
    assertThatThrownBy(() -> OpenApiCommandValidation.require("openapi-diff", List.of("oasdiff")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("must run the 'breaking' subcommand");
    assertThatThrownBy(
            () -> OpenApiCommandValidation.require("openapi-diff", List.of("oasdiff", "report")))
        .hasMessageContaining("must run the 'breaking' subcommand");
  }
}
