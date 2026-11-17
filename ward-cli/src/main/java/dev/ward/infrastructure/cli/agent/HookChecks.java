package dev.ward.infrastructure.cli.agent;

import dev.ward.application.agent.HookScope;
import dev.ward.application.gate.CheckService;
import dev.ward.application.gate.PromptScanner;

/**
 * The checks a hook runs after an agent edit or at turn end, and when a prompt is submitted.
 *
 * @param checks runs the configured quality gates
 * @param scope decides which projects a hook checks
 * @param prompts scans submitted prompts for secrets
 */
public record HookChecks(CheckService checks, HookScope scope, PromptScanner prompts) {
  /** Creates checks that always check the hook's own project and scan no prompts. */
  public HookChecks(final CheckService checks) {
    this(checks, HookScope.ALWAYS, PromptScanner.NONE);
  }

  /** Creates checks that scan no prompts. */
  public HookChecks(final CheckService checks, final HookScope scope) {
    this(checks, scope, PromptScanner.NONE);
  }
}
