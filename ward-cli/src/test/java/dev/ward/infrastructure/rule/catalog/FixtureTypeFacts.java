package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.application.rule.TypeFactsFixture;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Gives a fixture its type facts. A rule that needs types puts a {@code types.json} beside its
 * fixtures; {@link TypeFactsFixture} (#350) reads it, and the facts are answered for every unit and
 * span the rule asks about, with no resolver, classpath or {@code node_modules}. Without a {@code
 * types.json} the facts are unknown, so a rule that needs types reports not run, never a pass.
 */
final class FixtureTypeFacts {

  private FixtureTypeFacts() {}

  /**
   * Returns the facts of the fixture folder.
   *
   * @param typesJson the file beside the fixtures, if any
   * @throws IllegalArgumentException if the file is not valid fixture facts
   */
  static TypeFacts of(final Optional<Path> typesJson) {
    final UnitTypeFacts facts =
        typesJson.map(TypeFactsFixture::read).orElseGet(UnitTypeFacts::none);
    return (unit, spans, kinds) -> facts;
  }
}
