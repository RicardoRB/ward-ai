package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.QualityPreset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** The bodies of the Node tool configs init writes, without their ownership marker. */
public final class NodeConfigTemplates {
  private static final String LINE = "LINE";
  private static final String BRANCH = "BRANCH";
  private static final int PERCENT = 100;
  private static final String VITEST =
      """
      import { defineConfig } from "vitest/config";

      export default defineConfig({
        test: {
          coverage: {
            provider: "v8",
            thresholds: {
      {thresholds}
            },
          },
        },
      });
      """;

  private static final String JEST =
      """
      export default {
        coverageThreshold: {
          global: {
      {thresholds}
          },
        },
      };
      """;

  private NodeConfigTemplates() {}

  /** Returns a Vitest config that fails below {@code preset}'s coverage minimums. */
  public static String vitest(final QualityPreset preset) {
    return VITEST.replace("{thresholds}", properties(thresholds(preset), "        "));
  }

  /** Returns a Jest config that fails below {@code preset}'s global coverage minimums. */
  public static String jest(final QualityPreset preset) {
    return JEST.replace("{thresholds}", properties(thresholds(preset), "      "));
  }

  /** Returns Bun's test config, which measures lines but not branches. */
  public static String bunfig(final QualityPreset preset) {
    return "[test]\ncoverageThreshold = { lines = " + minimums(preset).get(LINE) + " }\n";
  }

  /** The preset's line and, when it sets one, branch minimum, as whole percentages. */
  private static Map<String, Integer> thresholds(final QualityPreset preset) {
    final Map<String, Double> minimums = minimums(preset);
    final Map<String, Integer> thresholds = new LinkedHashMap<>();
    thresholds.put("lines", percent(minimums.get(LINE)));
    if (minimums.containsKey(BRANCH)) {
      thresholds.put("branches", percent(minimums.get(BRANCH)));
    }
    return thresholds;
  }

  private static int percent(final double ratio) {
    return (int) Math.round(ratio * PERCENT);
  }

  // A custom selection uses the standard preset's minimums, as the test commands do.
  private static Map<String, Double> minimums(final QualityPreset preset) {
    return (preset == null ? QualityPreset.STANDARD : preset).rules().jacocoMinimums();
  }

  private static String properties(final Map<String, Integer> values, final String indent) {
    return values.entrySet().stream()
        .map(entry -> indent + entry.getKey() + ": " + entry.getValue() + ",")
        .collect(Collectors.joining("\n"));
  }
}
