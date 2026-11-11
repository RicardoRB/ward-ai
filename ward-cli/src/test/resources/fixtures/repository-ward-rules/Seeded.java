package seeded;

/** One violation per upstream tool that the repository's gates used to report. */
class Seeded {

  private static int count;

  /** Checkstyle MagicNumber: a literal that is neither a constant nor an ignored number. */
  int magic() {
    return 42;
  }

  /** PMD UnnecessaryReturn: a return that ends a void method. */
  void unnecessaryReturn() {
    return;
  }

  /** SpotBugs ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD: an instance method writes a static field. */
  void store() {
    count = 1;
  }
}
