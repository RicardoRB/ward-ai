package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Decides what applying each rule-file target would do, before any file is written. */
final class RuleFileActions {
  /** The marker of a Maven rule file that Ward wrote. */
  static final String MARKER = "<!-- managed-by: ward preset=";

  private RuleFileActions() {}

  /** What applying one target would do. */
  record Decision(NodeConfigTarget target, PlannedFileChange.Action action) {}

  /** Returns the decision for each target, in order. */
  static List<Decision> decide(final List<NodeConfigTarget> targets) throws IOException {
    final List<Decision> decisions = new ArrayList<>();
    for (final NodeConfigTarget target : targets) {
      decisions.add(new Decision(target, decide(target)));
    }
    return decisions;
  }

  /** Stops before any write when a target would overwrite a file Ward did not create. */
  static void refuseConflicts(final List<Decision> decisions) {
    final List<String> conflicts =
        decisions.stream()
            .filter(decision -> decision.action() == PlannedFileChange.Action.CONFLICT)
            .map(decision -> decision.target().detail())
            .toList();
    if (!conflicts.isEmpty()) {
      throw new WardException("Could not write Node config files: " + String.join("; ", conflicts));
    }
  }

  private static PlannedFileChange.Action decide(final NodeConfigTarget target) throws IOException {
    return switch (target.disposition()) {
      case KEEP -> PlannedFileChange.Action.PRESERVE_USER_OWNED;
      case CONFLICT -> PlannedFileChange.Action.CONFLICT;
      case WRITE -> writeAction(target.file(), target.content());
      case MOVE, REPLACE, REMOVE -> movedAction(target);
    };
  }

  /** Decides a target that moves or removes the user's file, which may already be gone. */
  private static PlannedFileChange.Action movedAction(final NodeConfigTarget target) {
    final boolean exists = Files.exists(target.file());
    return switch (target.disposition()) {
      case MOVE -> exists ? PlannedFileChange.Action.CONFLICT : PlannedFileChange.Action.CREATE;
      case REPLACE -> exists ? PlannedFileChange.Action.MODIFY : PlannedFileChange.Action.CREATE;
      default -> exists ? PlannedFileChange.Action.REMOVE : PlannedFileChange.Action.UNCHANGED;
    };
  }

  /** Decides what writing {@code content} to {@code file} would do. */
  private static PlannedFileChange.Action writeAction(final Path file, final String content)
      throws IOException {
    if (!Files.exists(file)) {
      return PlannedFileChange.Action.CREATE;
    }
    final String existing = Files.readString(file);
    if (!existing.startsWith(MARKER) && !NodeConfigFiles.owned(existing)) {
      return PlannedFileChange.Action.PRESERVE_USER_OWNED;
    }
    return existing.equals(content)
        ? PlannedFileChange.Action.UNCHANGED
        : PlannedFileChange.Action.MODIFY;
  }
}
