package dev.ward.infrastructure.rule.catalog;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The hook through which a fixture can give a rule type facts. A rule that needs types puts a
 * {@code types.json} beside its fixtures; the loader of the type-facts step (#307, pull request
 * #350) reads it and answers the type queries of that rule. That loader is not merged yet, so a
 * fixture with a {@code types.json} fails here with a message that says what to connect, instead of
 * running the rule without types and reporting a pass it did not earn.
 */
final class FixtureTypeFacts {

  private FixtureTypeFacts() {}

  /**
   * Checks that the rule's {@code types.json}, when present, can be used.
   *
   * @param typesJson the file beside the fixtures, if any
   * @return the problem to report, or empty when there is nothing to load
   */
  static Optional<String> problem(final Optional<Path> typesJson) {
    return typesJson.map(
        file ->
            file
                + " holds type facts, but the types.json loader of #350 is not on this branch."
                + " Replace this hook with that loader when #350 merges.");
  }
}
