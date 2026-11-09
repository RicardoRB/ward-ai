package dev.ward.analysis.application.node;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.ClassCondition;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.Filters;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.MemberCondition;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.NameCondition;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.NodeCondition;
import dev.ward.analysis.domain.rule.check.DecoratedClassCheck.ScopeCondition;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.tomlj.TomlParseResult;

/** The declarative checks of decorated classes, such as TypeScript's Angular decorators. */
final class DecoratedClassChecks {

  private static final String MESSAGE = "message";

  private DecoratedClassChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(entry("decorated-class", DecoratedClassChecks::create));
  }

  private static RuleCheck create(final TomlParseResult toml) {
    final Filters filters =
        new Filters(enclosing(toml), member(toml), node(toml), scope(toml), name(toml));
    return new DecoratedClassCheck(
        TomlValues.strings(toml, "kinds"), filters, TomlValues.required(toml, MESSAGE));
  }

  private static ClassCondition enclosing(final TomlParseResult toml) {
    final Map<Pattern, Pattern> entries = new LinkedHashMap<>();
    TomlValues.kindMap(toml, "classes")
        .forEach(
            (decorator, text) -> entries.put(Pattern.compile(decorator), Pattern.compile(text)));
    return new ClassCondition(
        pattern(toml, "class-decorator"),
        entries,
        Optional.ofNullable(toml.getString("implements-not")));
  }

  private static MemberCondition member(final TomlParseResult toml) {
    return new MemberCondition(
        pattern(toml, "member-decorator"),
        TomlValues.optionalStrings(toml, "member-has"),
        pattern(toml, "member-not-text"));
  }

  private static NodeCondition node(final TomlParseResult toml) {
    final boolean exemptActive =
        toml.getString("exempt-when") == null
            || Boolean.TRUE.equals(toml.getBoolean(toml.getString("exempt-when")));
    return new NodeCondition(
        pattern(toml, "text"),
        exemptActive ? pattern(toml, "exempt") : Optional.empty(),
        Boolean.TRUE.equals(toml.getBoolean("in-class-decorator")),
        pattern(toml, "not-inside-text"));
  }

  private static ScopeCondition scope(final TomlParseResult toml) {
    return new ScopeCondition(
        TomlValues.optionalStrings(toml, "inside"),
        TomlValues.optionalStrings(toml, "parent"),
        TomlValues.optionalStrings(toml, "not-child"));
  }

  private static NameCondition name(final TomlParseResult toml) {
    return new NameCondition(
        TomlValues.kindMap(toml, "heritage-missing"),
        pattern(toml, "alias"),
        TomlValues.optionalStrings(toml, "allowed-names"),
        Boolean.TRUE.equals(toml.getBoolean("aria")));
  }

  private static Optional<Pattern> pattern(final TomlParseResult toml, final String key) {
    return Optional.ofNullable(toml.getString(key)).map(Pattern::compile);
  }
}
