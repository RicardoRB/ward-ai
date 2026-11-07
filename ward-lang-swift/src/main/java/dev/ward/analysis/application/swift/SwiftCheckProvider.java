package dev.ward.analysis.application.swift;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ConstructorGroupCheck;
import dev.ward.analysis.domain.rule.check.HiddenFieldCheck;
import dev.ward.analysis.domain.rule.check.ImportSortCheck;
import dev.ward.analysis.domain.rule.check.JavadocMethodCheck;
import dev.ward.analysis.domain.rule.check.UnusedImportCheck;
import dev.ward.analysis.domain.rule.check.VariableDistanceCheck;
import java.util.Map;

/**
 * The declarative checks that only Swift rules use: the style checks of declarations and imports.
 */
public final class SwiftCheckProvider implements CheckProvider {

  private static final String MESSAGE = "message";

  @Override
  public Map<String, CheckFactory> checks() {
    return Map.of(
        "hidden-field",
        toml -> new HiddenFieldCheck(TomlValues.required(toml, MESSAGE)),
        "javadoc-method",
        toml -> new JavadocMethodCheck(TomlValues.required(toml, MESSAGE)),
        "constructor-group",
        toml -> new ConstructorGroupCheck(TomlValues.required(toml, MESSAGE)),
        "import-sort",
        toml -> new ImportSortCheck(TomlValues.required(toml, MESSAGE)),
        "unused-import",
        toml -> new UnusedImportCheck(TomlValues.required(toml, MESSAGE)),
        "variable-declaration-usage-distance",
        toml ->
            new VariableDistanceCheck(
                TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE)));
  }
}
