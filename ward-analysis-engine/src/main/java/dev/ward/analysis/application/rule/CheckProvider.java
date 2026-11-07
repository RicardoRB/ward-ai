package dev.ward.analysis.application.rule;

import java.util.Map;

/**
 * Contributes the declarative checks of one language module. Each check name must be registered by
 * exactly one provider; {@link CheckRegistry} rejects a name that two providers register.
 */
public interface CheckProvider {

  /** Returns the factories of this provider's checks, by the {@code check} name of a rule body. */
  Map<String, CheckFactory> checks();
}
