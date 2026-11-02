package dev.ward.infrastructure.init;

import java.nio.file.Path;

/**
 * One config file of a Node init plan. {@code content} is Ward's marked content, or {@code null}
 * when the file is kept, moved or removed. {@code detail} is a note for the plan and the output.
 */
public record ConfigTarget(Path file, Disposition disposition, String content, String detail) {

  /** What init does with one config file of the plan. */
  public enum Disposition {
    /** Writes Ward's content, unless the file already holds it or the user owns it. */
    WRITE,
    /** Writes Ward's content over a user file whose content moved to a base file. */
    REPLACE,
    /** Creates a base file that holds a user config's content, which Ward does not change. */
    MOVE,
    /** Keeps the user's file as written. */
    KEEP,
    /** Removes a user file whose content moved to a base file under another name. */
    REMOVE,
    /** Stops init: the base file name is taken by a file Ward did not create. */
    CONFLICT
  }

  /** Returns a target that writes Ward's {@code content} to {@code file}. */
  public static ConfigTarget write(final Path file, final String content) {
    return new ConfigTarget(file, Disposition.WRITE, content, null);
  }

  /** Returns a target that keeps the user's {@code file} as written, with a note for the plan. */
  public static ConfigTarget keep(final Path file, final String detail) {
    return new ConfigTarget(file, Disposition.KEEP, null, detail);
  }

  /** Returns a target that stops init, because {@code base} exists and Ward did not write it. */
  public static ConfigTarget conflict(final Path base) {
    return new ConfigTarget(
        base,
        Disposition.CONFLICT,
        null,
        base.getFileName() + " already exists, and Ward did not write it");
  }
}
