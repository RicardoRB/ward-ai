package dev.ward.application.rules;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.application.rules.RuleView.OptionView;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the {@link RuleView} of a rule. The description is the rule body's {@code message}; an
 * option's default is the body value of its parameter.
 */
final class RuleViews {
  private static final String MESSAGE = "message";

  private RuleViews() {}

  /** Returns the view of {@code rule}, reading its body with {@code bodies}. */
  static RuleView of(final RuleBodyReader bodies, final RuleDefinition rule) {
    final Map<String, Object> body = bodies.values(rule.body());
    final Object message = body.get(MESSAGE);
    return new RuleView(
        rule.id(),
        rule.languages().stream()
            .map(language -> language.name().toLowerCase(Locale.ROOT))
            .sorted()
            .toList(),
        rule.category(),
        rule.presets().stream()
            .sorted(Comparator.comparing(RulePreset::ordinal))
            .map(RulePreset::key)
            .toList(),
        message instanceof String text ? text : rule.id(),
        rule.options().stream().map(option -> option(option, body)).toList());
  }

  private static OptionView option(final RuleOption option, final Map<String, Object> body) {
    return new OptionView(
        option.name(),
        option.type().name().toLowerCase(Locale.ROOT).replace('_', '-'),
        body.get(option.parameter()),
        bound(option.min()),
        bound(option.max()),
        option.values());
  }

  /** Returns the bound, or {@code null} when the option declares none. */
  private static Long bound(final long value) {
    return value == Long.MIN_VALUE || value == Long.MAX_VALUE ? null : value;
  }
}
