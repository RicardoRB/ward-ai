package dev.ward.analysis.application.python;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import java.util.Map;

/**
 * The declarative checks that only Python rules use. Add a Python check to a family here, or a new
 * family to {@link #checks()}.
 */
public final class PythonCheckProvider implements CheckProvider {

  /** Creates the provider. */
  public PythonCheckProvider() {}

  @Override
  public Map<String, CheckFactory> checks() {
    return PythonScopeChecks.checks();
  }
}
