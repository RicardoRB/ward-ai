package dev.ward.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FrameworkTest {
  @ParameterizedTest
  @CsvSource({
    "SPRING_BOOT, Spring Boot",
    "QUARKUS, Quarkus",
    "MICRONAUT, Micronaut",
    "HELIDON, Helidon",
    "DROPWIZARD, Dropwizard",
    "JAVALIN, Javalin",
    "KTOR, Ktor",
    "VUE, Vue",
    "SVELTE, Svelte",
    "ASTRO, Astro",
    "ANGULAR, Angular",
    "NONE, None"
  })
  void everyFrameworkHasItsDisplayName(final Framework framework, final String displayName) {
    assertThat(framework.displayName()).isEqualTo(displayName);
  }
}
