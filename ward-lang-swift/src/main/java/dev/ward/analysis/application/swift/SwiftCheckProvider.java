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
import java.util.Map;

/**
 * The declarative checks that only Swift rules use: the style checks of declarations and imports.
 */
public final class SwiftCheckProvider implements CheckProvider {

  private static final String MESSAGE = "message";

  @Override
  public Map<String, CheckFactory> checks() {
    return Map.of(
        "swift-hidden-field",
        toml -> new SwiftHiddenFieldCheck(TomlValues.required(toml, MESSAGE)),
        "swift-javadoc-method",
        toml -> new JavadocMethodCheck(TomlValues.required(toml, MESSAGE)),
        "swift-constructor-group",
        toml -> new ConstructorGroupCheck(TomlValues.required(toml, MESSAGE)),
        "swift-import-sort",
        toml -> new ImportSortCheck(TomlValues.required(toml, MESSAGE)),
        "swift-unused-import",
        toml -> new UnusedImportCheck(TomlValues.required(toml, MESSAGE)),
        "swift-parameter-alignment",
        toml -> new SwiftParameterAlignmentCheck(TomlValues.required(toml, MESSAGE)),
        "swift-variable-distance",
        toml ->
            new VariableDistanceCheck(
                TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE)));
  }
}
