package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import java.util.Map;

/** The declarative checks that only TypeScript and JavaScript rules use. */
public final class NodeCheckProvider implements CheckProvider {

  @Override
  public Map<String, CheckFactory> checks() {
    return DecoratedClassChecks.checks();
  }
}
