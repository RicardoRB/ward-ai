package dev.ward.analysis.application.jvm;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;

/**
 * The Java and Kotlin rule files packaged in this module. Add a new rule file here, in alphabetical
 * order; no other module lists it.
 */
public final class JvmRuleFiles implements RuleFileProvider {

  private static final List<String> FILES =
      List.of(
          "rules/duplication-jvm.toml",
          "rules/java/architecture.toml",
          "rules/java/bestpractices.toml",
          "rules/java/blocks.toml",
          "rules/java/checkstyle-coding.toml",
          "rules/java/checkstyle-misc.toml",
          "rules/java/checkstyle-regexp.toml",
          "rules/java/coding.toml",
          "rules/java/correctness.toml",
          "rules/java/dataflow.toml",
          "rules/java/declarations.toml",
          "rules/java/design.toml",
          "rules/java/errorprone-gl.toml",
          "rules/java/errorprone-part2.toml",
          "rules/java/errorprone-standard-2.toml",
          "rules/java/errorprone-types.toml",
          "rules/java/errorprone.toml",
          "rules/java/imports.toml",
          "rules/java/javadoc-layout.toml",
          "rules/java/javadoc.toml",
          "rules/java/modifiers.toml",
          "rules/java/naming.toml",
          "rules/java/object-methods.toml",
          "rules/java/pmd-errorprone.toml",
          "rules/java/security.toml",
          "rules/java/semgrep-crypto.toml",
          "rules/java/size.toml",
          "rules/java/spotbugs-bad-practice.toml",
          "rules/java/strict-declarations.toml",
          "rules/kotlin/coroutines.toml",
          "rules/kotlin/exceptions.toml",
          "rules/kotlin/naming.toml",
          "rules/kotlin/performance.toml",
          "rules/kotlin/potential-bugs.toml",
          "rules/kotlin/size.toml");

  @Override
  public List<String> ruleFiles() {
    return FILES;
  }
}
