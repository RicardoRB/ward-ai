package seeded;

/** One violation per upstream tool that the repository's gates used to report. */
class Seeded {

  /** Checkstyle MagicNumber: a literal that is neither a constant nor an ignored number. */
  int magic() {
    return 42;
  }

  /** PMD UnnecessaryReturn: a return that ends a void method. */
  void unnecessaryReturn() {
    return;
  }

  /** SpotBugs SR_NOT_CHECKED: the byte count that skip returns is ignored. */
  void notChecked(java.io.InputStream in) throws java.io.IOException {
    in.skip(5);
  }
}
