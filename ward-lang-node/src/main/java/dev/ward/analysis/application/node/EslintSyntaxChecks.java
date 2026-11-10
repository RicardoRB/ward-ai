package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NodeConstantBinaryExpressionCheck;
import dev.ward.analysis.domain.rule.check.NodeExtraLabelCheck;
import dev.ward.analysis.domain.rule.check.NodeMisleadingCharacterClassCheck;
import dev.ward.analysis.domain.rule.check.NodeThisBeforeSuperCheck;
import dev.ward.analysis.domain.rule.check.NodeUselessBackreferenceCheck;
import java.util.Map;

/** The ESLint core checks that read the syntax tree or the text of the source (issue #326). */
final class EslintSyntaxChecks {

  private static final String MESSAGE = "message";

  private EslintSyntaxChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        Map.entry(
            "node-no-constant-binary-expression",
            (CheckFactory)
                toml ->
                    new NodeConstantBinaryExpressionCheck(
                        TomlValues.required(toml, "operand-message"),
                        TomlValues.required(toml, "short-circuit-message"))),
        Map.entry(
            "node-no-this-before-super",
            (CheckFactory)
                toml -> new NodeThisBeforeSuperCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-no-misleading-character-class",
            (CheckFactory)
                toml -> new NodeMisleadingCharacterClassCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry("node-strict-directive", EslintScriptChecks::strictDirective),
        Map.entry(
            "node-no-useless-backreference",
            (CheckFactory)
                toml -> new NodeUselessBackreferenceCheck(TomlValues.required(toml, "message"))),
        Map.entry(
            "node-no-extra-label",
            (CheckFactory) toml -> new NodeExtraLabelCheck(TomlValues.required(toml, MESSAGE))));
  }
}
