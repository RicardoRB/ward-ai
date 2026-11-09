package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The TypeScript and JavaScript rule files packaged in this module. Add a new rule file here, in
 * alphabetical order; no other module lists it.
 */
public final class NodeRuleFiles implements RuleFileProvider {

  private static final List<String> FILES =
      List.of(
          "rules/duplication-node.toml",
          "rules/node/angular.toml",
          "rules/node/biome-complexity.toml",
          "rules/node/biome-correctness.toml",
          "rules/node/biome-nursery.toml",
          "rules/node/biome-performance.toml",
          "rules/node/biome-suspicious.toml",
          "rules/node/blocks.toml",
          "rules/node/coding.toml",
          "rules/node/dead-code.toml",
          "rules/node/metrics.toml",
          "rules/node/misc.toml",
          "rules/node/naming.toml",
          "rules/node/problem.toml",
          "rules/node/regexp.toml",
          "rules/node/semgrep-angular.toml",
          "rules/node/semgrep-config.toml",
          "rules/node/semgrep-crypto.toml",
          "rules/node/semgrep-headers.toml",
          "rules/node/semgrep-injection.toml",
          "rules/node/semgrep-react.toml",
          "rules/node/semgrep-secrets.toml",
          "rules/node/semgrep-web.toml",
          "rules/node/sizes.toml",
          "rules/node/style-coding.toml",
          "rules/node/style-javadoc.toml",
          "rules/node/style-linter.toml",
          "rules/node/style-typescript.toml",
          "rules/node/suggestion.toml");

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
