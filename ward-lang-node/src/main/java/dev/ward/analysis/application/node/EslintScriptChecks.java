package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NodeStrictDirectiveCheck;
import java.util.LinkedHashMap;
import java.util.Map;
import org.tomlj.TomlParseResult;

/**
 * The ESLint core checks whose option is the kind of source the rule reads: a script or a module.
 */
final class EslintScriptChecks {

  /** The ESLint message ids of the strict rule, each with the TOML key of its message. */
  private static final Map<String, String> STRICT_MESSAGES =
      Map.ofEntries(
          Map.entry("function", "message-function"),
          Map.entry("global", "message-global"),
          Map.entry("multiple", "message-multiple"),
          Map.entry("never", "message-never"),
          Map.entry("unnecessary", "message-unnecessary"),
          Map.entry("module", "message-module"),
          Map.entry("unnecessaryInClasses", "message-unnecessary-in-classes"),
          Map.entry("nonSimpleParameterList", "message-non-simple-parameter-list"),
          Map.entry("wrap", "message-wrap"));

  private EslintScriptChecks() {}

  static NodeStrictDirectiveCheck strictDirective(final TomlParseResult toml) {
    final Map<String, String> messages = new LinkedHashMap<>();
    STRICT_MESSAGES.forEach((id, key) -> messages.put(id, TomlValues.required(toml, key)));
    return new NodeStrictDirectiveCheck(
        NodeStrictDirectiveCheck.Mode.named(TomlValues.required(toml, "mode")), messages);
  }
}
