package demo.repository;

/** Stores the greeting prefix. */
public final class GreetingRepository {
  private final String prefix;

  /**
   * Creates the repository.
   *
   * @param prefix the text put before every name
   */
  public GreetingRepository(final String prefix) {
    this.prefix = prefix;
  }

  /**
   * Returns the greeting prefix.
   *
   * @return the prefix
   */
  public String prefix() {
    return prefix;
  }
}
