package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.python.DigestUse.Kind;
import java.util.Optional;

/**
 * Where the climb of {@link DigestClimb} goes next: a final use, or the index of the node that is
 * now the value.
 *
 * @param use the final use, when the climb ends
 * @param next the index of the node that carries the value on, when the climb goes on
 */
record DigestStep(Optional<DigestUse> use, int next) {
  static DigestStep done(final Kind kind, final String detail) {
    return new DigestStep(Optional.of(DigestUse.of(kind, detail)), 0);
  }

  static DigestStep up(final int next) {
    return new DigestStep(Optional.empty(), next);
  }
}
