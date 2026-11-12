import { writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Assertion } from "../src/evidence";
import { type LiveContext, type LiveDriver } from "../src/live";

// Live Claude Code driver. `claude -p` runs one headless turn in the fixture; Claude Code itself
// discovers .claude/settings.json and dispatches the hooks. CLAUDE_CONFIG_DIR is isolated by the
// fixture, so no user or global settings, plugins or hooks are loaded.
// Source: https://code.claude.com/docs/en/hooks.

const TURN_TIMEOUT_MS = 240_000;

function turn(context: LiveContext, prompt: string) {
  return context.run(
    [context.runtime.executable, "-p", prompt, "--output-format", "stream-json", "--verbose", "--permission-mode", "acceptEdits"],
    TURN_TIMEOUT_MS,
  );
}

const invoked = (context: LiveContext) => context.invocations().filter((i) => i.argv[0] === "hook");

export const claudeCodeDriver: LiveDriver = {
  harness: "claude-code",
  missingPrerequisites: (env) => (env.ANTHROPIC_API_KEY ? [] : ["ANTHROPIC_API_KEY"]),
  scenarios: [
    {
      id: "edit-check-feedback",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Create src/Smoke.java containing `class Smoke {}`. Then quote any hook feedback you received.");
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native PostToolUse hook invoked ward", passed: invoked(context).length >= 1 },
          { name: "agent received the Ward failure", passed: result.stdout.includes("format") },
        ];
      },
    },
    {
      id: "stop-feedback-limit",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Reply with the single word DONE and do nothing else.");
        const stops = invoked(context);
        return [
          { name: "runtime turn completed", passed: !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "Stop hook invoked ward more than once (blocked, then continued)", passed: stops.length >= 2, detail: `${stops.length} invocation(s)` },
          { name: "the turn still ended within the deadline", passed: !result.timedOut },
        ];
      },
    },
  ],
};
