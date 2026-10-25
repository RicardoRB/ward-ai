package demo.service;

/**
 * A greeting and who it is for.
 *
 * @param name who is greeted
 * @param text the greeting
 */
public record Greeting(String name, String text) {
  /**
   * Returns whether the greeting names its person.
   *
   * @return whether the text contains the name
   */
  public boolean personal() {
    return text.contains(name);
  }
}
