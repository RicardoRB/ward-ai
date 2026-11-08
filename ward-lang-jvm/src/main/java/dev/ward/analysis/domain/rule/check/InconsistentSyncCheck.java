package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a field that a class accesses with the lock on {@code this} held most of the time but not
 * always, with the counts and thresholds of SpotBugs' FindInconsistentSync2: at least half of the
 * accesses are locked, the unlocked ones are not mostly reads, the field is written and read
 * outside constructors, and the unlocked accesses are not only in getters. The classes and fields
 * it cannot read are left out.
 */
public final class InconsistentSyncCheck implements RuleCheck {

  private static final String CLASS = "classDeclaration";
  private static final int MIN_SYNC_PERCENT = 50;
  private static final int PERCENT = 100;
  private static final int WRITE_BIAS = 2;
  private static final double UNSYNC_FACTOR = 1.6;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field and {@code {max}} the percentage of
   *     locked accesses
   */
  public InconsistentSyncCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS))) {
      if (readable(visit)) {
        SyncModel.of(visit.node()).ifPresent(model -> report(model, matches));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean readable(final SyntaxWalk.Visit visit) {
    final String header = SyntaxWalk.text(visit.node());
    final String head = header.substring(0, Math.max(0, header.indexOf('{')));
    return JavaNodes.modifiers(visit).stream().noneMatch(m -> m.startsWith("@NotThreadSafe"))
        && !head.contains("Servlet");
  }

  private void report(final SyncModel model, final List<RuleMatch> out) {
    final Map<String, int[]> counts = SyncAccesses.scan(model);
    model
        .fields()
        .forEach(
            (name, id) ->
                percent(counts.get(name))
                    .ifPresent(
                        percent ->
                            out.add(
                                CheckSupport.at(
                                    id.span(),
                                    CheckSupport.fill(message, name, String.valueOf(percent))))));
  }

  /** Returns the percentage of locked accesses when the field is reported. */
  private static Optional<Integer> percent(final int... c) {
    final int readLocked = c[SyncAccesses.READ_LOCKED];
    final int writeLocked = c[SyncAccesses.WRITE_LOCKED];
    final int nullLocked = c[SyncAccesses.NULL_LOCKED];
    final int readUnlocked = c[SyncAccesses.READ_UNLOCKED];
    final int writeUnlocked = c[SyncAccesses.WRITE_UNLOCKED];
    final int nullUnlocked = c[SyncAccesses.NULL_UNLOCKED];
    final int extra = writeUnlocked > 0 ? nullLocked : 0;
    final int locked = readLocked + writeLocked + nullLocked;
    final int unlocked = readUnlocked + writeUnlocked + nullUnlocked;
    final int biasedLocked = readLocked + WRITE_BIAS * (writeLocked + nullLocked + extra);
    final int biasedUnlocked = readUnlocked + WRITE_BIAS * writeUnlocked;
    final boolean reported =
        unlocked > 0
            && locked > 0
            && c[SyncAccesses.LOCAL_LOCKS] > 0
            && writeLocked + writeUnlocked > 0
            && readLocked + readUnlocked > 0
            && !manyUnlockedReads(readUnlocked, biasedUnlocked, biasedLocked)
            && PERCENT * locked / (locked + unlocked) >= MIN_SYNC_PERCENT
            && c[SyncAccesses.GETTER] < unlocked;
    return reported
        ? Optional.of(PERCENT * locked / (locked + unlocked + nullUnlocked))
        : Optional.empty();
  }

  private static boolean manyUnlockedReads(
      final int readUnlocked, final int biasedUnlocked, final int biasedLocked) {
    return readUnlocked > 0 && (int) (UNSYNC_FACTOR * (biasedUnlocked - 1)) > biasedLocked;
  }
}
