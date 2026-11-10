package dev.ward.analysis.application.node;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.BiomeAmbiguousAnchorTextCheck;
import dev.ward.analysis.domain.rule.check.BiomeCommentTextCheck;
import dev.ward.analysis.domain.rule.check.BiomeGoogleFontDisplayCheck;
import dev.ward.analysis.domain.rule.check.BiomeHeaderScopeCheck;
import dev.ward.analysis.domain.rule.check.BiomeLabelWithoutControlCheck;
import dev.ward.analysis.domain.rule.check.BiomeMisplacedListElementCheck;
import dev.ward.analysis.domain.rule.check.BiomeRedundantAltCheck;
import dev.ward.analysis.domain.rule.check.BiomeSuspiciousSemicolonCheck;
import dev.ward.analysis.domain.rule.check.BiomeUnsafeIframeSandboxCheck;
import dev.ward.analysis.domain.rule.check.BiomeValidAutocompleteCheck;
import dev.ward.analysis.domain.rule.check.BiomeValidLangCheck;
import java.util.Map;

/** The JSX checks that read only the element and its text, such as sandbox, comments and alt. */
final class BiomeJsxChecks {

  private static final String MESSAGE = "message";

  private BiomeJsxChecks() {}

  /** The checks this family provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "node-unsafe-iframe-sandbox",
            toml -> new BiomeUnsafeIframeSandboxCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-comment-text",
            toml -> new BiomeCommentTextCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-header-scope",
            toml -> new BiomeHeaderScopeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-redundant-alt",
            toml -> new BiomeRedundantAltCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-ambiguous-anchor-text",
            toml ->
                new BiomeAmbiguousAnchorTextCheck(
                    TomlValues.strings(toml, "words"), TomlValues.required(toml, MESSAGE))),
        entry(
            "node-valid-lang", toml -> new BiomeValidLangCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-label-without-control",
            toml -> new BiomeLabelWithoutControlCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-misplaced-list-element",
            toml -> new BiomeMisplacedListElementCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-suspicious-semicolon",
            toml -> new BiomeSuspiciousSemicolonCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-google-font-display",
            toml -> new BiomeGoogleFontDisplayCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "node-valid-autocomplete",
            toml -> new BiomeValidAutocompleteCheck(TomlValues.required(toml, MESSAGE))));
  }
}
