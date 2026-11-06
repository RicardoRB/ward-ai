package dev.ward.analysis.domain.jvm.types;

/** The access flags of class files that Ward reads. */
public final class Flags {
  /** {@code public}. */
  public static final int PUBLIC = 0x0001;

  /** {@code private}. */
  public static final int PRIVATE = 0x0002;

  /** {@code protected}. */
  public static final int PROTECTED = 0x0004;

  /** {@code static}. */
  public static final int STATIC = 0x0008;

  /** {@code final}. */
  public static final int FINAL = 0x0010;

  /** A varargs method. */
  public static final int VARARGS = 0x0080;

  /** An interface. */
  public static final int INTERFACE = 0x0200;

  /** {@code abstract}. */
  public static final int ABSTRACT = 0x0400;

  /** An annotation type. */
  public static final int ANNOTATION = 0x2000;

  /** An enum. */
  public static final int ENUM = 0x4000;

  /** A synthetic member, such as a bridge method. */
  public static final int SYNTHETIC = 0x1000;

  private Flags() {}
}
