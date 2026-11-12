import { writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Assertion } from "../src/evidence";
import { type LiveContext, type LiveDriver } from "../src/live";

// Live OpenCode V2 driver. It launches the pinned runtime with a private standalone server in the
// fixture, so the runtime itself discovers .opencode/plugins and dispatches native tool events.
// A model turn is required to produce tool calls; the model is named explicitly and credentials
// come only from the allowlist.

const TURN_TIMEOUT_MS = 240_000;

async function turn(context: LiveContext, prompt: string) {
  const model = process.env.WARD_COMPAT_OPENCODE_MODEL as string;
  return context.run(
    [context.runtime.executable, "run", "--standalone", "--format", "json", "--auto", "--model", model, "--print-logs", "--log-level", "info", prompt],
    TURN_TIMEOUT_MS,
  );
}

const CHECK = ["check", "--profile", "edit", "--format", "json", "--learn-after", "3"];
const sameArgv = (a: string[], b: string[]) => JSON.stringify(a) === JSON.stringify(b);

export const opencodeDriver: LiveDriver = {
  harness: "opencode",
  missingPrerequisites(env) {
    const missing: string[] = [];
    if (!env.WARD_COMPAT_OPENCODE_MODEL) missing.push("WARD_COMPAT_OPENCODE_MODEL (provider/model)");
    if (!["ANTHROPIC_API_KEY", "OPENAI_API_KEY", "OPENROUTER_API_KEY", "GEMINI_API_KEY"].some((name) => env[name])) {
      missing.push("a model provider API key from the credential allowlist");
    }
    return missing;
  },
  scenarios: [
    {
      // Model-free: the runtime discovers and loads project plugins before any model call, and an
      // unroutable model ends the turn without contacting a provider.
      id: "plugin-activation",
      requiresCredentials: false,
      async execute(context): Promise<Assertion[]> {
        const result = await context.run(
          [context.runtime.executable, "run", "--standalone", "--format", "json", "--model", "ward-compat/unroutable", "--print-logs", "--log-level", "info", "activation probe"],
          120_000,
        );
        const lines = result.stderr.split("\n");
        const loading = (file: string) => lines.some((l) => l.includes('msg="loading plugin"') && l.includes(`/.opencode/plugins/${file}`));
        const failed = (file: string) => lines.some((l) => l.includes('message="failed to load plugin"') && l.includes(`/.opencode/plugins/${file}`));
        return [
          { name: "runtime exited within the deadline", passed: !result.timedOut },
          { name: "runtime discovered ward-edit-write.js", passed: loading("ward-edit-write.js") },
          { name: "ward-edit-write.js loaded without error", passed: loading("ward-edit-write.js") && !failed("ward-edit-write.js") },
        ];
      },
    },
    {
      id: "edit-check-feedback",
      async execute(context): Promise<Assertion[]> {
        // The fixture's stub command gate runs the stub mvnw; a FAIL file makes it fail deterministically.
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Use the write tool to create the file src/Smoke.java containing `class Smoke {}`. Then stop.");
        const checks = context.invocations().filter((i) => sameArgv(i.argv, CHECK));
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native edit event invoked ward check", passed: checks.length >= 1, detail: `${checks.length} check invocation(s)` },
          { name: "check ran in the session location", passed: checks.every((i) => i.cwd.endsWith("/project")) },
          { name: "agent-visible tool result carries the failure", passed: result.stdout.includes("Ward quality check failed") },
        ];
      },
    },
    { id: "reload-cleanup", notImplemented: "needs a long-lived session that can reload plugins between turns" },
  ],
};
