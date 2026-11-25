package dev.ward.analysis.domain.python;

/**
 * What one use of a digest does with it, as {@link DigestUses} reads it from the syntax.
 *
 * @param kind the class of the use
 * @param detail what the use is, for a message; empty when there is nothing to say
 */
public record DigestUse(Kind kind, String detail) {
  /** The classes of use, from harmless to unseen. */
  public enum Kind {
    /** The digest is fed data, assigned to a variable (its reads are uses of their own) or dropped. */
    NEUTRAL,
    /** The digest only names or keys something: a file name, a path, a key of a mapping. */
    BENIGN,
    /** The digest is compared, or is an input of an authentication function. */
    SECURITY,
    /** The digest is returned, so its callers decide what it is for. */
    ESCAPE,
    /** The digest goes where the analysis does not follow it. */
    UNKNOWN
  }

  static DigestUse of(final Kind kind) {
    return new DigestUse(kind, "");
  }

  static DigestUse of(final Kind kind, final String detail) {
    return new DigestUse(kind, detail);
  }
}
