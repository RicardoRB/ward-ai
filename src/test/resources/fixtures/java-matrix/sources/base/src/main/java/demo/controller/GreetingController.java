package demo.controller;

import demo.service.Greeter;

/** Answers greeting requests. */
public final class GreetingController {
  private final Greeter greeter;

  /**
   * Creates the controller.
   *
   * @param greeter the service that builds greetings
   */
  public GreetingController(final Greeter greeter) {
    this.greeter = greeter;
  }

  /**
   * Answers a request.
   *
   * @param name who to greet
   * @return the response body
   */
  public String handle(final String name) {
    return greeter.greet(name.trim());
  }
}
