package dev.ward.analysis.application.python;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The Python rule files packaged in this module. Add a new rule file here, in alphabetical order;
 * no other module lists it.
 */
public final class PythonRuleFiles implements RuleFileProvider {

  private static final List<String> FILES =
      List.of(
          "rules/duplication-python.toml",
          "rules/python-structural.toml",
          "rules/python/bugbear.toml",
          "rules/python/checkstyle.toml",
          "rules/python/flake8-2020.toml",
          "rules/python/flake8-bandit.toml",
          "rules/python/flake8-comprehensions.toml",
          "rules/python/flake8-debugger.toml",
          "rules/python/flake8-errmsg.toml",
          "rules/python/flake8-implicit-str-concat.toml",
          "rules/python/flake8-self.toml",
          "rules/python/flake8-simplify.toml",
          "rules/python/flake8-slots.toml",
          "rules/python/pycodestyle.toml",
          "rules/python/pyflakes.toml",
          "rules/python/pygrep-hooks.toml",
          "rules/python/pylint.toml",
          "rules/python/refurb-calls.toml",
          "rules/python/refurb.toml");

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
