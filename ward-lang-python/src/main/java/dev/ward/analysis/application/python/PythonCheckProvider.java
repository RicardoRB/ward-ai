package dev.ward.analysis.application.python;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.CheckRegistry;
import java.util.List;
import java.util.Map;

/**
 * The declarative checks that only Python rules use. Add a Python check to a family here, or a new
 * family to {@link #checks()}.
 */
public final class PythonCheckProvider implements CheckProvider {

  @Override
  public Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(PythonScopeChecks.checks(), PythonOrderChecks.checks()));
  }
}
