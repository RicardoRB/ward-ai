package dev.ward.infrastructure.cli.agent;

import dev.ward.application.agent.HookScope;
import dev.ward.application.gate.CheckService;

/**
 * The checks a hook runs after an agent edit or at turn end.
 *
 * @param checks runs the configured quality gates
 * @param scope decides which projects a hook checks
 */
public record HookChecks(CheckService checks, HookScope scope) {
  /** Creates checks that always check the hook's own project. */
  public HookChecks(final CheckService checks) {
    this(checks, HookScope.ALWAYS);
  }
}
