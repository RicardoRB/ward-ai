package dev.ward.analysis.application.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleFileReader;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A rule's {@code text} pattern runs on whole declarations, so it must not recurse per character.
 */
class RuleTextPatternsTest {
  private static final Pattern TEXT = Pattern.compile("(?m)^text = '(.*)'$");
  private static final int LONG = 5_000;

  @Test
  void noTextPatternOverflowsTheStackOnLongInput() {
    final List<String> inputs =
        List.of(
            "\"\"\"" + "ab ".repeat(LONG),
            "\"" + "x".repeat(LONG) + "\"",
            "a=\"" + "x".repeat(LONG) + "\";",
            "f(" + "(a)".repeat(LONG) + ")",
            "class A{" + "int x;".repeat(LONG) + "}");
    final List<String> overflowing = new ArrayList<>();
    for (final String file : new JvmRuleFiles().ruleFiles()) {
      for (final RuleDefinition rule : RuleFileReader.read(file)) {
        final Matcher found = TEXT.matcher(rule.body());
        while (found.find()) {
          final Pattern pattern = Pattern.compile(found.group(1));
          if (overflows(pattern, inputs)) {
            overflowing.add(rule.id());
          }
        }
      }
    }
    assertThat(overflowing).isEmpty();
  }

  private static boolean overflows(final Pattern pattern, final List<String> inputs) {
    for (final String input : inputs) {
      try {
        pattern.matcher(input).find();
      } catch (final StackOverflowError error) {
        return true;
      }
    }
    return false;
  }
}
