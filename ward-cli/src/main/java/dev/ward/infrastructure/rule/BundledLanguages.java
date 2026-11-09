package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.jvm.JvmCheckProvider;
import dev.ward.analysis.application.jvm.JvmRuleFiles;
import dev.ward.analysis.application.node.NodeRuleFiles;
import dev.ward.analysis.application.python.PythonCheckProvider;
import dev.ward.analysis.application.python.PythonRuleFiles;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.RuleFileProvider;
import dev.ward.analysis.application.swift.SwiftCheckProvider;
import dev.ward.analysis.application.swift.SwiftLintCheckProvider;
import dev.ward.analysis.application.swift.SwiftRuleFiles;
import java.util.List;

/**
 * The language modules Ward bundles. Adding a rule or a check to a language touches only that
 * language's module; this list changes only when a language is added. The Dagger module of the rule
 * bindings and the tests that build the bundled rules without Dagger both read it.
 */
public final class BundledLanguages {

  private BundledLanguages() {}

  /** Returns the rule file lists of every bundled language module. */
  public static List<RuleFileProvider> ruleFiles() {
    return List.of(
        new JvmRuleFiles(), new NodeRuleFiles(), new PythonRuleFiles(), new SwiftRuleFiles());
  }

  /** Returns the check providers of the shared checks and of every bundled language module. */
  public static List<CheckProvider> checkProviders() {
    return List.of(
        new CoreCheckProvider(),
        new JvmCheckProvider(),
        new PythonCheckProvider(),
        new SwiftCheckProvider(),
        new SwiftLintCheckProvider());
  }

  /** Returns the registry of every bundled check. */
  public static CheckRegistry checkRegistry() {
    return new CheckRegistry(checkProviders());
  }
}
