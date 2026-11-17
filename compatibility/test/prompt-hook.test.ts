import { afterAll, describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { recordedInvocations } from "../src/execute";
import { createFixture, wardLauncher, type Fixture } from "../src/fixture";
import { writeRecordingWrapper } from "../src/live";
import { REPO_ROOT } from "../src/manifest";
import { cleanupOwnedProcesses, run, SYSTEM_PATH } from "../src/process";

// Runs the prompt hook that the real `ward integrate <agent> --prompt-hook` registers, the way each
// agent documents launching it: the registered command, through the shell, with the event on stdin.
// A fake secret from the rules/secrets fixtures must be rejected in the agent's own format, and a
// clean prompt must pass without output. Evidence level: deterministic.

const SECRET = readFileSync(join(REPO_ROOT, "ward-cli/src/test/resources/rules/secrets/github-pat/violating.txt"), "utf8").trim();
const RULE = "ward:secrets/github-pat";

interface Agent {
  id: string;
  /** The registered command of the prompt event, read from the generated configuration. */
  command: (root: string) => string;
  payload: (prompt: string) => object;
  /** What the rejection must contain. */
  rejects: (stdout: string) => boolean;
}

const json = (root: string, file: string) => JSON.parse(readFileSync(join(root, file), "utf8"));

const AGENTS: Agent[] = [
  {
    id: "claude-code",
    command: (root) => json(root, ".claude/settings.json").hooks.UserPromptSubmit[0].hooks[0].command.replace("${CLAUDE_PROJECT_DIR}", root),
    payload: (prompt) => ({ hook_event_name: "UserPromptSubmit", prompt }),
    rejects: (out) => JSON.parse(out).decision === "block",
  },
  {
    id: "codex",
    command: (root) => json(root, ".codex/hooks.json").hooks.UserPromptSubmit[0].hooks[0].command,
    payload: (prompt) => ({ hook_event_name: "UserPromptSubmit", prompt }),
    rejects: (out) => JSON.parse(out).decision === "block",
  },
  {
    id: "factory-droid",
    command: (root) => json(root, ".factory/hooks.json").UserPromptSubmit[0].hooks[0].command,
    payload: (prompt) => ({ hook_event_name: "UserPromptSubmit", prompt }),
    rejects: (out) => JSON.parse(out).decision === "block",
  },
  {
    id: "gemini",
    command: (root) => json(root, ".gemini/settings.json").hooks.BeforeAgent[0].hooks[0].command,
    payload: (prompt) => ({ hook_event_name: "BeforeAgent", prompt }),
    rejects: (out) => JSON.parse(out).decision === "deny",
  },
  {
    id: "cursor",
    command: (root) => json(root, ".cursor/hooks.json").hooks.beforeSubmitPrompt[0].command,
    payload: (prompt) => ({ hook_event_name: "beforeSubmitPrompt", prompt, attachments: [] }),
    rejects: (out) => JSON.parse(out).continue === false,
  },
];

const fixtures: Fixture[] = [];
afterAll(() => {
  fixtures.forEach((fixture) => fixture.cleanup());
  cleanupOwnedProcesses();
});

describe("prompt hook of each supported agent", () => {
  for (const agent of AGENTS) {
    test(`${agent.id}: a prompt with a secret is rejected and a clean prompt passes`, async () => {
      const ward = wardLauncher(process.env)!;
      const fixture = await createFixture({ harness: agent.id, ward, promptHook: true });
      fixtures.push(fixture);
      const bin = join(dirname(fixture.root), "ward-bin");
      const log = join(dirname(fixture.root), "ward-log");
      writeRecordingWrapper(bin, log, ward);
      await run(["/usr/bin/git", "init", "-q"], { cwd: fixture.root, env: fixture.env, timeoutMs: 30_000 });
      const env = { ...fixture.env, PATH: `${bin}:${SYSTEM_PATH}`, CLAUDE_PROJECT_DIR: fixture.root };
      const launch = (prompt: string) =>
        run(["/bin/sh", "-c", agent.command(fixture.root)], { cwd: fixture.root, env, stdin: JSON.stringify({ cwd: fixture.root, ...agent.payload(prompt) }), timeoutMs: 60_000 });

      const blocked = await launch(`use this token\n${SECRET}`);
      expect(blocked.exitCode).toBe(0);
      expect(agent.rejects(blocked.stdout)).toBe(true);
      expect(blocked.stdout).toContain(RULE);
      expect(blocked.stdout).toContain("line 2, column 1");
      expect(blocked.stdout + blocked.stderr).not.toContain(SECRET);

      const started = Date.now();
      const clean = await launch("Rename the class Foo.");
      const elapsed = Date.now() - started;
      // The JVM may print its own warnings on stderr; the hook itself says nothing on a clean prompt.
      expect([clean.exitCode, clean.stdout.trim()]).toEqual([0, ""]);
      expect(clean.stderr).not.toContain("Ward");
      console.log(`${agent.id}: clean prompt took ${elapsed} ms`);

      expect(recordedInvocations(log).length).toBe(2);
    }, 180_000);
  }
});
