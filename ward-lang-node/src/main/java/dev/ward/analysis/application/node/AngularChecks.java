package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import java.util.Map;

/** The TypeScript and JavaScript checks that only the angular-eslint rules use (issue #328). */
final class AngularChecks {

  private AngularChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return DecoratedClassChecks.checks();
  }
}
