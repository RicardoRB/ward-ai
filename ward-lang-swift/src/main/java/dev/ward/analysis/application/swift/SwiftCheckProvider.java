package dev.ward.analysis.application.swift;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.ConstructorGroupCheck;
import dev.ward.analysis.domain.rule.check.ImportSortCheck;
import dev.ward.analysis.domain.rule.check.JavadocMethodCheck;
import dev.ward.analysis.domain.rule.check.UnusedImportCheck;
import dev.ward.analysis.domain.rule.check.VariableDistanceCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicateEnumCasesCheck;
import dev.ward.analysis.domain.swift.SwiftDuplicatedDictionaryKeyCheck;
import dev.ward.analysis.domain.swift.SwiftForWhereCheck;
import dev.ward.analysis.domain.swift.SwiftGenericTypeNameCheck;
import dev.ward.analysis.domain.swift.SwiftHiddenFieldCheck;
import dev.ward.analysis.domain.swift.SwiftNsobjectPreferIsEqualCheck;
import dev.ward.analysis.domain.swift.SwiftParameterAlignmentCheck;
import dev.ward.analysis.domain.swift.SwiftRedundantSendableCheck;
import dev.ward.analysis.domain.swift.SwiftRedundantVoidReturnCheck;
import dev.ward.analysis.domain.swift.SwiftStaticOverFinalClassCheck;
import java.util.Map;

/**
 * The declarative checks that only Swift rules use: the style checks of declarations and imports,
 * and the lint checks of declarations and statements.
 */
public final class SwiftCheckProvider implements CheckProvider {

  private static final String MESSAGE = "message";

  @Override
  public Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        Map.entry(
            "swift-hidden-field",
            toml -> new SwiftHiddenFieldCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-javadoc-method",
            toml -> new JavadocMethodCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-constructor-group",
            toml -> new ConstructorGroupCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-import-sort", toml -> new ImportSortCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-unused-import",
            toml -> new UnusedImportCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-parameter-alignment",
            toml -> new SwiftParameterAlignmentCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-redundant-void-return",
            toml -> new SwiftRedundantVoidReturnCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-duplicate-enum-cases",
            toml -> new SwiftDuplicateEnumCasesCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-for-where", toml -> new SwiftForWhereCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-static-over-final-class",
            toml -> new SwiftStaticOverFinalClassCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-duplicated-dictionary-key",
            toml -> new SwiftDuplicatedDictionaryKeyCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-generic-type-name",
            toml -> new SwiftGenericTypeNameCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-nsobject-prefer-isequal",
            toml -> new SwiftNsobjectPreferIsEqualCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-redundant-sendable",
            toml -> new SwiftRedundantSendableCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "swift-variable-distance",
            toml ->
                new VariableDistanceCheck(
                    TomlValues.integer(toml, "max"), TomlValues.required(toml, MESSAGE))));
  }
}
