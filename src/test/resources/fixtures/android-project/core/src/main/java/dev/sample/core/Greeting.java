package dev.sample.core;

public final class Greeting {
  private Greeting() {}

  public static String of(final String name) {
    return "Hello, " + name;
  }
}
