package demo.service;

import demo.repository.GreetingRepository;

/** Greets people. */
public final class Greeter {
  private static final String NOBODY = "world";
  private final GreetingRepository repository;

  /**
   * Creates the greeter.
   *
   * @param repository where the greeting prefix comes from
   */
  public Greeter(final GreetingRepository repository) {
    this.repository = repository;
  }

  /**
   * Returns a greeting.
   *
   * @param name who to greet
   * @return the greeting
   */
  public String greet(final String name) {
    if (name == null || name.isEmpty()) {
      return repository.prefix() + NOBODY;
    }
    return repository.prefix() + name;
  }
}
