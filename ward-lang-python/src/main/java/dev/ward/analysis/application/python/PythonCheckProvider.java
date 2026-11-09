package dev.ward.analysis.application.python;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.CheckRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The declarative checks that only Python rules use. Add a Python check to a family here, or a new
 * family to {@link #checks()}.
 */
public final class PythonCheckProvider implements CheckProvider {

  @Override
  public Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> checks =
        new HashMap<>(
            CheckRegistry.union(
                List.of(
                    PythonScopeChecks.checks(),
                    PythonOrderChecks.checks(),
                    PythonGeneralChecks.checks(),
                    PythonStyleChecks.checks(),
                    PythonShapeChecks.checks(),
                    PylintPartAChecks.checks())));
    // A whole-run check: UnusedDefinitionFindings reports it, so no unit has a match here.
    checks.put("unused-definition", toml -> root -> List.of());
    // A whole-run graph check: CyclicImportRule reports it, so no unit has a match here.
    checks.put("cyclic-import", toml -> root -> List.of());
    return Map.copyOf(checks);
  }
}
