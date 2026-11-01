package dev.ward.infrastructure.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The dependency-cruiser rules match layer directories anywhere in a path, so a root config run
 * from the workspace root also checks imports between members.
 */
class DependencyCruiserMemberPathsTest {
  private static final QualityPreset.ArchitectureExtras NO_EXTRAS =
      QualityPreset.STANDARD.rules().architectureExtras();

  @Test
  void hexagonalRuleMatchesDomainInsideAMemberAndInfrastructureInAnother() {
    final String config = DependencyCruiserConfigSource.render("hexagonal", NO_EXTRAS, true, false);

    assertThat(config)
        .contains("from: { path: \"(^|/)(domain|application)/\" }")
        .contains("to: { path: \"(^|/)(adapter|infrastructure)/\" }");
    assertThat(
            Pattern.compile("(^|/)(domain|application)/")
                .matcher("apps/web/src/domain/order.ts")
                .find())
        .isTrue();
    assertThat(
            Pattern.compile("(^|/)(adapter|infrastructure)/")
                .matcher("packages/ui/src/infrastructure/db.ts")
                .find())
        .isTrue();
  }
}
