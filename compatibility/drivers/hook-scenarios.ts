import { writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Assertion } from "../src/evidence";
import { type LiveContext, type LiveScenario } from "../src/live";

// Live scenarios shared by harnesses that run Ward through a generated `ward.sh post` wrapper: one
// headless turn per scenario, judged by recorded Ward invocations and file contents.

type Turn = (context: LiveContext, prompt: string) => ReturnType<LiveContext["run"]>;

const checks = (context: LiveContext) => context.invocations().filter((i) => i.argv[0] === "hook");

/** Edit feedback and the turn-end continuation limit. */
export function hookScenarios(turn: Turn): LiveScenario[] {
  return [
    {
      id: "edit-check-feedback",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Create src/Smoke.java containing `class Smoke {}`. Then quote any hook feedback you received.");
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native post-edit hook invoked ward", passed: checks(context).length >= 1 },
          { name: "agent received the Ward failure", passed: result.stdout.includes("format") },
        ];
      },
    },
    {
      id: "turn-end-feedback-limit",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Reply with the single word DONE and do nothing else.");
        const ends = checks(context);
        return [
          { name: "runtime turn completed", passed: !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "turn-end hook invoked ward more than once (blocked, then continued)", passed: ends.length >= 2, detail: `${ends.length} invocation(s)` },
          { name: "the turn still ended within the deadline", passed: !result.timedOut },
        ];
      },
    },
  ];
}
