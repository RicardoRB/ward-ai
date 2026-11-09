package dev.ward.analysis.application.node;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.BiomeCommentTextCheck;
import dev.ward.analysis.domain.rule.check.BiomeUnsafeIframeSandboxCheck;
import java.util.Map;

/** The JSX checks that read only the element and its text: sandbox and comment text. */
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
            toml -> new BiomeCommentTextCheck(TomlValues.required(toml, MESSAGE))));
  }
}
