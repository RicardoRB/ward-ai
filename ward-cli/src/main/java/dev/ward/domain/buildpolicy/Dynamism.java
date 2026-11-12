package dev.ward.domain.buildpolicy;

/** How a version can change over time. */
public enum Dynamism {
  /** An exact version. */
  NONE,
  /** A range that a lockfile pins. */
  RANGE,
  /** A version that stays dynamic whether or not a lockfile exists. */
  ALWAYS
}
