package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurboOutputTest {
  @Test
  void splitsGroupedTurboOutputIntoPackageBlocks() {
    final String output =
        """
        web:check-types: src/page.tsx(3,7): error TS2322: Type 'string' is not assignable.
        ui:check-types: src/Button.tsx(1,1): error TS1005: ';' expected.
        """;

    assertThat(TurboOutput.parse(output))
        .containsExactly(
            new TurboOutput.Block(
                "web", "src/page.tsx(3,7): error TS2322: Type 'string' is not assignable.\n"),
            new TurboOutput.Block("ui", "src/Button.tsx(1,1): error TS1005: ';' expected.\n"));
  }

  @Test
  void dropsPackageManagerBannersWhenPrefixedLinesExist() {
    final String output =
        """
        > web@0.0.0 check-types /repo/apps/web
        > tsc --noEmit --pretty false

        web:check-types: src/page.tsx(3,7): error TS2322: Type 'string' is not assignable.
        """;

    assertThat(TurboOutput.parse(output))
        .containsExactly(
            new TurboOutput.Block(
                "web", "src/page.tsx(3,7): error TS2322: Type 'string' is not assignable.\n"));
  }

  @Test
  void keepsTheWholeOutputWhenNothingIsPrefixed() {
    final String output = "src/page.tsx(3,7): error TS2322: Type 'string' is not assignable.\n";

    assertThat(TurboOutput.parse(output)).containsExactly(new TurboOutput.Block("", output));
  }

  @Test
  void joinsLinesOfOnePackageIntoOneBlock() {
    final String output =
        """
        web:lint: [
        web:lint:   {"filePath":"/repo/apps/web/src/a.ts","messages":[]}
        web:lint: ]
        """;

    assertThat(TurboOutput.parse(output))
        .containsExactly(
            new TurboOutput.Block(
                "web", "[\n  {\"filePath\":\"/repo/apps/web/src/a.ts\",\"messages\":[]}\n]\n"));
  }
}
