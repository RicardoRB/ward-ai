package dev.ward.analysis.application.node;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.node.NodeSafeValues;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.NodeUnsafeValueCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Map;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/** The TypeScript and JavaScript security checks that read the value of a sink. */
final class SecurityChecks {
  private SecurityChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(entry("node-unsafe-value", SecurityChecks::unsafeValue));
  }

  private static RuleCheck unsafeValue(final TomlParseResult toml) {
    final String argument = TomlValues.required(toml, "argument");
    final String safe = TomlValues.required(toml, "safe");
    if (!"first".equals(argument) && !"last".equals(argument)) {
      throw new RuleConfigurationException("argument must be first or last");
    }
    return new NodeUnsafeValueCheck(
        Pattern.compile(TomlValues.required(toml, "text")),
        "first".equals(argument),
        mode(safe),
        TomlValues.required(toml, "message")
    );
  }

  private static NodeSafeValues.Mode mode(final String safe) {
    return switch (safe) {
      case "markup" -> NodeSafeValues.Mode.MARKUP;
      case "local-host" -> NodeSafeValues.Mode.LOCAL_HOST;
      default -> throw new RuleConfigurationException("safe must be markup or local-host");
    };
  }
}
