class Passing {
  /** The value. */
  int value() {
    return 1;
  }

  /** Uses {@code x}. */
  int other() {
    return 2;
  }
}

class Mentions {
  /** A method that is annotated @Deprecated in its text. */
  int old() {
    return 3;
  }
}
