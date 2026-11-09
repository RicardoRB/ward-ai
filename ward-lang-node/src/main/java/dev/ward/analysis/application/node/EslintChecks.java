package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.GetterReturnCheck;
import java.util.Map;
import org.tomlj.TomlParseResult;

/** The TypeScript and JavaScript checks that only the ESLint core rules use (issue #326). */
final class EslintChecks {

  private static final String MESSAGE = "message";

  private EslintChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.of("node-getter-return", EslintChecks::getterReturn);
  }

  private static GetterReturnCheck getterReturn(final TomlParseResult toml) {
    return new GetterReturnCheck(
        Boolean.TRUE.equals(toml.getBoolean("allow-implicit")), TomlValues.required(toml, MESSAGE));
  }
}
