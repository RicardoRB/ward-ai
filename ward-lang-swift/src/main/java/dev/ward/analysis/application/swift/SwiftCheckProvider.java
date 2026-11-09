package dev.ward.analysis.application.swift;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ConstructorGroupCheck;
import dev.ward.analysis.domain.rule.check.ImportSortCheck;
import dev.ward.analysis.domain.rule.check.JavadocMethodCheck;
import dev.ward.analysis.domain.rule.check.UnusedImportCheck;
import dev.ward.analysis.domain.rule.check.VariableDistanceCheck;
import dev.ward.analysis.domain.swift.SwiftHiddenFieldCheck;
import dev.ward.analysis.domain.swift.SwiftParameterAlignmentCheck;
import java.util.LinkedHashMap;
import java.util.Map;

/** The declarative style checks that only Swift rules use: declarations, parameters and imports. */
public final class SwiftCheckProvider implements CheckProvider {

  private static final String MESSAGE = "message";

  @Override
  public Map<String, CheckFactory> checks() {
    final Map<String, CheckFactory> checks = new LinkedHashMap<>();
    declarationChecks(checks);
    return Map.copyOf(checks);
  }

  private static void declarationChecks(final Map<String, CheckFactory> checks) {
    checks.put(
        "swift-constructor-group",
        toml -> new ConstructorGroupCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-hidden-field",
        toml -> new SwiftHiddenFieldCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-import-sort", toml -> new ImportSortCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-javadoc-method", toml -> new JavadocMethodCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-parameter-alignment",
        toml -> new SwiftParameterAlignmentCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-unused-import", toml -> new UnusedImportCheck(TomlValues.required(toml, MESSAGE)));
    checks.put(
        "swift-variable-distance",
        toml ->
            new VariableDistanceCheck(
                TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE)));
  }
}
