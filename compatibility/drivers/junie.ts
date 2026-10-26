import type { Fixture } from "../src/fixture";
import type { LiveContext, LiveDriver } from "../src/live";
import { hookScenarios, prepareSmokeFixture } from "./hook-scenarios";

// Live Junie CLI driver. `junie --config-location .junie/config.json` loads the project's hooks (Junie
// ignores them otherwise), and `--auth` takes the token for one headless turn, as in the Junie headless
// docs (https://junie.jetbrains.com/docs/junie-headless.html). The prompt is the final positional
// argument. Junie treats non-interactive runs as trusted, so no trust bootstrap is needed.
// Unverified until a real run: the docs name no auto-approve flag, so a tool call that asks for approval
// may stall the turn until TURN_TIMEOUT_MS; the first live run decides whether a flag is needed here.

const TURN_TIMEOUT_MS = 240_000;

function prepare(fixture: Fixture): void {
  prepareSmokeFixture(fixture);
}

const turn = (context: LiveContext, prompt: string) =>
  context.run(
    [
      context.runtime.executable,
      "--config-location",
      ".junie/config.json",
      `--auth=${context.env.JUNIE_API_KEY ?? ""}`,
      prompt,
    ],
    TURN_TIMEOUT_MS,
  );

export const junieDriver: LiveDriver = {
  harness: "junie",
  missingPrerequisites: (env) => (env.JUNIE_API_KEY ? [] : ["JUNIE_API_KEY"]),
  prepare,
  scenarios: hookScenarios(turn),
};
