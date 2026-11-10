package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.CheckRegistry;
import java.util.List;
import java.util.Map;

/**
 * The declarative checks that only TypeScript and JavaScript rules use. Add a check to the family
 * of its rule step; each family is its own class so parallel steps never edit the same file.
 */
public final class NodeCheckProvider implements CheckProvider {

  @Override
  public Map<String, CheckFactory> checks() {
    return CheckRegistry.union(
        List.of(
            AngularChecks.checks(),
            BiomeChecks.checks(),
            DeadCodeChecks.checks(),
            EslintChecks.checks(),
            EslintScopeChecks.checks(),
            EslintSyntaxChecks.checks(),
            SemgrepChecks.checks(),
            StyleChecks.checks(),
            TypeScriptEslintChecks.checks()));
  }
}
