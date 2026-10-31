package dev.codeja;

/** Dereferences a null string. */
public final class Bug {
  public int length() {
    final String text = null;
    return text.length();
  }
}
