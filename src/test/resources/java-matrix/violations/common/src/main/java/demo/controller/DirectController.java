package demo.controller;

import demo.repository.GreetingRepository;

/** Reads the repository directly, skipping the service layer. */
public final class DirectController {
  private final GreetingRepository repository;

  /**
   * Creates the controller.
   *
   * @param repository the repository it should not use
   */
  public DirectController(final GreetingRepository repository) {
    this.repository = repository;
  }

  /**
   * Returns the prefix.
   *
   * @return the prefix
   */
  public String prefix() {
    return repository.prefix();
  }
}
