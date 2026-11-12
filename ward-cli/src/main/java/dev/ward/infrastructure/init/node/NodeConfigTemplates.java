package dev.ward.infrastructure.init.node;

/** The bodies of the Node tool configs init writes, without their ownership marker. */
public final class NodeConfigTemplates {
  /**
   * Vitest reports lcov beside its text table with V8 coverage. It sets no threshold: the {@code
   * coverage} check of {@code ward.toml} holds the minimums.
   */
  private static final String VITEST =
      """
      import { defineConfig } from "vitest/config";

      export default defineConfig({
        test: {
          coverage: {
            provider: "v8",
            reporter: ["text", "lcov"],
          },
        },
      });
      """;

  private NodeConfigTemplates() {}

  /** Returns the Vitest config that writes the text and lcov coverage reports. */
  public static String vitest() {
    return VITEST;
  }
}
