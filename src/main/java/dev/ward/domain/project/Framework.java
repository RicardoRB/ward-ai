package dev.ward.domain.project;

import java.util.EnumSet;
import java.util.Set;

/** The application framework detected in a project. */
public enum Framework {
  SPRING_BOOT("Spring Boot"),
  QUARKUS("Quarkus"),
  MICRONAUT("Micronaut"),
  HELIDON("Helidon"),
  DROPWIZARD("Dropwizard"),
  JAVALIN("Javalin"),
  KTOR("Ktor"),
  VUE("Vue"),
  SVELTE("Svelte"),
  ASTRO("Astro"),
  ANGULAR("Angular"),
  NEXT("Next.js"),
  NEST("NestJS"),
  FASTIFY("Fastify"),
  EXPRESS("Express"),
  DJANGO("Django"),
  FASTAPI("FastAPI"),
  FLASK("Flask"),
  ANDROID("Android"),
  NONE("None");

  private static final Set<Framework> SERVER_FRAMEWORKS =
      EnumSet.of(
          SPRING_BOOT,
          QUARKUS,
          MICRONAUT,
          HELIDON,
          DROPWIZARD,
          JAVALIN,
          KTOR,
          DJANGO,
          FASTAPI,
          FLASK,
          NEXT,
          NEST,
          FASTIFY,
          EXPRESS);

  private final String displayName;

  Framework(final String displayName) {
    this.displayName = displayName;
  }

  /** Returns the name shown to users. */
  public String displayName() {
    return displayName;
  }

  /**
   * Returns whether the framework serves HTTP routes, so its project may publish an API contract.
   */
  public boolean servesHttp() {
    return SERVER_FRAMEWORKS.contains(this);
  }
}
