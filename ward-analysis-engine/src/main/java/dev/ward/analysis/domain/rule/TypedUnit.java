package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.Objects;

/**
 * A unit with the type facts a rule asked for. The facts are {@link UnitTypeFacts#none()} for a
 * rule that reads no types.
 *
 * @param unit the source unit
 * @param types the type facts of the unit
 */
public record TypedUnit(SourceUnit unit, UnitTypeFacts types) {

  /**
   * Validates the parts.
   *
   * @throws NullPointerException if a part is missing
   */
  public TypedUnit {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(types, "types");
  }
}
