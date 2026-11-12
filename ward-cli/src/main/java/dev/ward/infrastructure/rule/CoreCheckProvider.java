package dev.ward.infrastructure.rule;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.CognitiveComplexityCheck;
import dev.ward.analysis.domain.rule.check.CognitiveTokens;
import dev.ward.analysis.domain.rule.check.CountedKinds;
import dev.ward.analysis.domain.rule.check.DeclarationFilter;
import dev.ward.analysis.domain.rule.check.DescendantCountCheck;
import dev.ward.analysis.domain.rule.check.LinesCheck;
import dev.ward.analysis.domain.rule.check.PrecedingCommentCheck;
import dev.ward.analysis.domain.rule.check.ReassignedBindingCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.rule.check.TaintCheck;
import dev.ward.analysis.domain.rule.check.TextPatternCheck;
import dev.ward.analysis.domain.rule.check.UndefinedNameCheck;
import dev.ward.analysis.domain.rule.check.UnreachableCodeCheck;
import dev.ward.analysis.domain.rule.check.UnreferencedNameCheck;
import dev.ward.analysis.domain.rule.check.UnusedAssignmentCheck;
import dev.ward.analysis.domain.rule.check.UnusedBindingCheck;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlParseResult;

/**
 * The declarative checks that every language shares. A check that only one language uses belongs to
 * the {@link CheckProvider} of that language's module.
 */
public final class CoreCheckProvider implements CheckProvider {

  private static final String KINDS = "kinds";
  private static final String MAX = "max";
  private static final String MESSAGE = "message";
  private static final String MODIFIERS = "modifiers";
  private static final String REQUIRED = "required";
  private static final String EXCLUDED = "excluded";
  private static final String SPECS = "specs";
  private static final String COUNT_EMPTY = "count-empty";

  @Override
  public Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "lines",
            toml ->
                new LinesCheck(
                    TomlValues.strings(toml, KINDS),
                    TomlValues.integer(toml, MAX),
                    TomlValues.required(toml, MESSAGE),
                    Optional.ofNullable(toml.getBoolean(COUNT_EMPTY)).orElse(true))),
        entry("descendant-count", CoreCheckProvider::descendantCount),
        // A whole-run rule: DuplicateCodeFindings reports its copies, so no unit has a match here.
        entry("duplicate-code", toml -> root -> List.of()),
        entry("cognitive-complexity", CoreCheckProvider::cognitiveComplexity),
        entry("text-pattern", CoreCheckProvider::textPattern),
        entry(
            "unreferenced-name",
            toml -> new UnreferencedNameCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unused-assignment",
            toml -> new UnusedAssignmentCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "undefined-name",
            toml ->
                new UndefinedNameCheck(
                    TomlValues.required(toml, MESSAGE),
                    Set.copyOf(TomlValues.optionalStrings(toml, "env")))),
        entry("unused-binding", toml -> new UnusedBindingCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "unreachable-code",
            toml -> new UnreachableCodeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "reassigned-binding",
            toml ->
                ReassignedBindingCheck.of(
                    TomlValues.strings(toml, "binding-kinds"), TomlValues.required(toml, MESSAGE))),
        entry(
            "taint",
            toml ->
                new TaintCheck(
                    TaintSpecFiles.load(SpecPaths.of(toml, SPECS)),
                    TomlValues.required(toml, MESSAGE))),
        entry("preceding-comment", CoreCheckProvider::precedingComment),
        entry("secret-scan", SecretScanFactory::create));
  }

  private static RuleCheck descendantCount(final TomlParseResult toml) {
    return new DescendantCountCheck(
        TomlValues.strings(toml, KINDS),
        countedKinds(toml, "counted"),
        TomlValues.optionalInteger(toml, "base", 0),
        TomlValues.integer(toml, MAX),
        TomlValues.required(toml, MESSAGE));
  }

  private static CountedKinds countedKinds(final TomlParseResult toml, final String key) {
    return new CountedKinds(
        TomlValues.strings(toml, key),
        TomlValues.kindMap(toml, "requires-sibling"),
        TomlValues.kindMap(toml, "cancelled-by"));
  }

  private static RuleCheck cognitiveComplexity(final TomlParseResult toml) {
    return new CognitiveComplexityCheck(
        TomlValues.strings(toml, KINDS),
        new CognitiveTokens(
            countedKinds(toml, "structures"),
            TomlValues.strings(toml, "flat"),
            TomlValues.optionalStrings(toml, "chaining")),
        TomlValues.integer(toml, MAX),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck textPattern(final TomlParseResult toml) {
    return new TextPatternCheck(
        filter(toml),
        TomlValues.required(toml, "name"),
        TomlValues.optionalStrings(toml, "skip"),
        TomlValues.required(toml, "pattern"),
        TomlValues.required(toml, MESSAGE));
  }

  private static RuleCheck precedingComment(final TomlParseResult toml) {
    return new PrecedingCommentCheck(
        filter(toml),
        Optional.ofNullable(toml.getString("exempt-annotation")),
        TomlValues.required(toml, MESSAGE));
  }

  private static DeclarationFilter filter(final TomlParseResult toml) {
    return new DeclarationFilter(
        TomlValues.strings(toml, KINDS),
        TomlValues.strings(toml, MODIFIERS),
        Optional.ofNullable(toml.getString(REQUIRED)),
        Optional.ofNullable(toml.getString(EXCLUDED)));
  }
}
