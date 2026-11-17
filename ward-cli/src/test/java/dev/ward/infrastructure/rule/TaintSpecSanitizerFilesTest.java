package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the kinds that a sanitizer entry of a specification file takes. */
class TaintSpecSanitizerFilesTest {
  /** A member read is accepted as a sanitizer of its receiver, such as lastPathComponent. */
  @Test
  void memberReadIsAcceptedAsASanitizer() {
    final TaintSpec spec = TaintSpecFiles.load(List.of("taint/swift/ward-path-injection.toml"));

    assertThat(spec.sanitizers())
        .anySatisfy(
            entry -> {
              assertThat(entry.kind()).isEqualTo(FlowSiteKind.MEMBER_READ);
              assertThat(entry.name()).isEqualTo("lastPathComponent");
            }
        );
  }
}
