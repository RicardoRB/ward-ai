package dev.ward.application.gate.openapi;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Accepts only allowed flags on OpenAPI gate commands, so no flag, ruleset, or ignore file can
 * silence a rule.
 */
public final class OpenApiCommandValidation {
  private static final String FAIL_ON = "--fail-on";
  private static final String FORMAT = "--format";
  private static final String COLOR = "--color";
  private static final String LANG = "--lang";

  private static final Policy DIFF =
      new Policy(
          "breaking",
          Map.ofEntries(
              Map.entry("-o", FAIL_ON),
              Map.entry(FAIL_ON, FAIL_ON),
              Map.entry("-f", FORMAT),
              Map.entry(FORMAT, FORMAT),
              Map.entry(COLOR, COLOR),
              Map.entry("-l", LANG),
              Map.entry(LANG, LANG),
              Map.entry("--flatten-allof", "--flatten-allof"),
              Map.entry("--flatten-params", "--flatten-params"),
              Map.entry("--include-path-params", "--include-path-params")),
          Map.of(
              FAIL_ON,
              Set.of("ERR", "WARN")::contains,
              FORMAT,
              Set.of(
                      "githubactions",
                      "html",
                      "json",
                      "junit",
                      "markdown",
                      "markup",
                      "singleline",
                      "text",
                      "yaml")
                  ::contains,
              COLOR,
              Set.of("auto", "always", "never")::contains,
              LANG,
              Set.of("en", "ru", "pt-br", "es")::contains),
          List.of(new Requirement(FAIL_ON, "requires --fail-on ERR or --fail-on WARN")));

  private OpenApiCommandValidation() {}

  /**
   * Checks the command of the OpenAPI gate {@code gate} of the given {@code type}: the lint gate
   * runs Ward's own rules and takes no command, the others take a command of the allowed shape.
   *
   * @throws WardException if the lint gate has a command, or another gate's command is not allowed
   */
  public static void requireFor(final String type, final String gate, final List<String> command) {
    if (SupportedQualityGates.OPENAPI_LINT.equals(type)) {
      if (!command.isEmpty()) {
        throw new WardException(
            "Quality gate '"
                + gate
                + "' has a command, which is not allowed: openapi-lint runs"
                + " Ward's own rules.");
      }
    } else {
      require(gate, command);
    }
  }

  /**
   * Checks the command of the OpenAPI gate {@code gate}.
   *
   * @throws WardException if the command uses a flag outside the allowed set, sets one twice, or
   *     misses a required one
   */
  public static void require(final String gate, final List<String> command) {
    final Policy policy = DIFF;
    if (command.size() < 2 || !policy.subcommand().equals(command.get(1))) {
      throw error(gate, "must run the '" + policy.subcommand() + "' subcommand");
    }
    final Set<String> seen = parse(gate, policy, command.subList(2, command.size()));
    for (final Requirement requirement : policy.required()) {
      if (!seen.contains(requirement.flag())) {
        throw error(gate, requirement.message());
      }
    }
  }

  /** Returns the canonical names of the flags in {@code arguments}, each allowed at most once. */
  private static Set<String> parse(
      final String gate, final Policy policy, final List<String> arguments) {
    final List<String> tokens = splitInlineValues(arguments);
    final Set<String> seen = new HashSet<>();
    int index = 0;
    while (index < tokens.size()) {
      final String name = policy.names().get(tokens.get(index));
      if (name == null) {
        throw error(
            gate,
            "does not allow '"
                + tokens.get(index)
                + "': only flags that cannot silence a rule are allowed, and Ward appends the spec");
      }
      if (policy.values().containsKey(name)) {
        index++;
        checkValue(gate, policy, name, index < tokens.size() ? tokens.get(index) : "");
      }
      if (!seen.add(name)) {
        throw error(gate, "sets '" + name + "' more than once; set it once");
      }
      index++;
    }
    return seen;
  }

  /** Splits {@code --flag=value} into the flag and its value, as the tools themselves do. */
  private static List<String> splitInlineValues(final List<String> arguments) {
    return arguments.stream()
        .flatMap(
            argument ->
                argument.startsWith("--") && argument.indexOf('=') > 0
                    ? Stream.of(
                        argument.substring(0, argument.indexOf('=')),
                        argument.substring(argument.indexOf('=') + 1))
                    : Stream.of(argument))
        .toList();
  }

  private static void checkValue(
      final String gate, final Policy policy, final String name, final String value) {
    if (policy.values().get(name).test(value)) {
      return;
    }
    final String message =
        policy.required().stream()
            .filter(requirement -> requirement.flag().equals(name))
            .map(Requirement::message)
            .findFirst()
            .orElse("does not accept '" + value + "' for '" + name + "'");
    throw error(gate, message);
  }

  private static WardException error(final String gate, final String message) {
    return new WardException("Quality gate '" + gate + "' " + message + ".");
  }

  /**
   * The flags one tool subcommand may receive.
   *
   * @param subcommand the subcommand that must follow the executable
   * @param names every allowed spelling mapped to its canonical name
   * @param values the accepted values of each flag that takes one
   * @param required the flags that must be present, with the message when one is missing
   */
  private record Policy(
      String subcommand,
      Map<String, String> names,
      Map<String, Predicate<String>> values,
      List<Requirement> required) {}

  /** A flag that must be present, and what Ward says when it is missing or has a wrong value. */
  private record Requirement(String flag, String message) {}
}
