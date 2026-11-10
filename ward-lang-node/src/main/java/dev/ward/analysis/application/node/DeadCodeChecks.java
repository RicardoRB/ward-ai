package dev.ward.analysis.application.node;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.NodeCatalogCheck;
import dev.ward.analysis.domain.rule.check.NodeDuplicateExportCheck;
import dev.ward.analysis.domain.rule.check.NodeOptionalPeerCheck;
import dev.ward.analysis.domain.rule.check.NodeUnlistedBinaryCheck;
import dev.ward.analysis.domain.rule.check.NodeUnlistedImportCheck;
import dev.ward.analysis.domain.rule.check.NodeUnresolvedImportCheck;
import dev.ward.analysis.domain.rule.check.NodeUnusedDependencyCheck;
import dev.ward.analysis.domain.rule.check.NodeUnusedExportCheck;
import dev.ward.analysis.domain.rule.check.NodeUnusedFileCheck;
import dev.ward.analysis.domain.rule.check.NodeUnusedMemberCheck;
import java.util.Map;

/** The TypeScript and JavaScript checks that only the dead-code (knip) rules use (issue #331). */
final class DeadCodeChecks {

  private static final String MESSAGE = "message";

  private DeadCodeChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        Map.entry(
            "node-unresolved-import",
            toml -> new NodeUnresolvedImportCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-unlisted-import",
            toml -> new NodeUnlistedImportCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-unused-dependency",
            toml ->
                new NodeUnusedDependencyCheck(
                    TomlValues.required(toml, MESSAGE), TomlValues.required(toml, "section"))),
        Map.entry(
            "node-optional-peer",
            toml -> new NodeOptionalPeerCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-catalog",
            toml -> new NodeCatalogCheck(TomlValues.required(toml, MESSAGE), false)),
        Map.entry(
            "node-catalog-reference",
            toml -> new NodeCatalogCheck(TomlValues.required(toml, MESSAGE), true)),
        Map.entry(
            "node-unused-file",
            toml -> new NodeUnusedFileCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-unused-export",
            toml -> new NodeUnusedExportCheck(TomlValues.required(toml, MESSAGE), false)),
        Map.entry(
            "node-unused-type",
            toml -> new NodeUnusedExportCheck(TomlValues.required(toml, MESSAGE), true)),
        Map.entry(
            "node-unlisted-binary",
            toml -> new NodeUnlistedBinaryCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-duplicate-export",
            toml -> new NodeDuplicateExportCheck(TomlValues.required(toml, MESSAGE))),
        Map.entry(
            "node-unused-enum-member",
            toml -> new NodeUnusedMemberCheck(TomlValues.required(toml, MESSAGE), "enum")),
        Map.entry(
            "node-unused-namespace-member",
            toml -> new NodeUnusedMemberCheck(TomlValues.required(toml, MESSAGE), "namespace")));
  }
}
