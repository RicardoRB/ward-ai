import { writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Assertion } from "../src/evidence";
import { type LiveContext, type LiveDriver } from "../src/live";

// Live Amp driver. Amp loads project plugins from .amp/plugins only when PLUGINS=all is set, and
// only in the binary-installed CLI. Execute mode (-x) runs one agent turn; --plugin-ready-timeout
// waits for plugins before the turn so tool.result/agent.end handlers are active.
// Sources: https://ampcode.com/manual/plugin-api, https://ampcode.com/docs/cli/execute-mode.

const TURN_TIMEOUT_MS = 240_000;
const CHECK = ["hook", "native", "--agent", "amp", "--event-stdin"];
const same = (a: string[], b: string[]) => JSON.stringify(a) === JSON.stringify(b);

function turn(context: LiveContext, prompt: string) {
  return context.run(
    ["/usr/bin/env", "PLUGINS=all", context.runtime.executable, "--plugin-ready-timeout", "30", "-x", prompt],
    TURN_TIMEOUT_MS,
  );
}

export const ampDriver: LiveDriver = {
  harness: "amp",
  missingPrerequisites: (env) => (env.AMP_API_KEY ? [] : ["AMP_API_KEY"]),
  scenarios: [
    {
      id: "edit-check-feedback",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Create the file src/Smoke.java containing `class Smoke {}`. Then report any tool output you received verbatim.");
        const checks = context.invocations().filter((i) => same(i.argv, CHECK));
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native tool.result invoked ward", passed: checks.length >= 1, detail: `${checks.length} check invocation(s)` },
          { name: "agent reported the Ward failure it received", passed: result.stdout.includes("Ward") },
        ];
      },
    },
    { id: "turn-end-continuation", notImplemented: "needs a trusted fixture that produces turn-end findings through a gate the harness can fail" },
  ],
};
