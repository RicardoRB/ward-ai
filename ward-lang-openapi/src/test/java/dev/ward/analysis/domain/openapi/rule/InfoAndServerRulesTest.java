package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InfoAndServerRulesTest {

  private static final String HEAD = "openapi: 3.0.3\ninfo:\n  title: T\n";

  @Test
  void infoContactAndDescriptionAndLicenseReportAtTheInfoKey() {
    assertThat(RuleFixtures.positions("info-contact", HEAD)).containsExactly("2:1");
    assertThat(RuleFixtures.positions("info-description", HEAD)).containsExactly("2:1");
    assertThat(RuleFixtures.positions("info-license", HEAD)).containsExactly("2:1");
    assertThat(RuleFixtures.positions("info-description", HEAD + "  description: d\n")).isEmpty();
    assertThat(RuleFixtures.positions("info-contact", "openapi: 3.0.3\n")).isEmpty();
  }

  @Test
  void aLicenseWithoutANameIsReportedAtTheLicenseKey() {
    assertThat(RuleFixtures.positions("info-license", HEAD + "  license:\n    url: u\n"))
        .containsExactly("4:3");
  }

  @Test
  void contactPropertiesNameWhatIsMissing() {
    assertThat(RuleFixtures.run("contact-properties", HEAD + "  contact:\n    name: n\n"))
        .extracting(OpenApiFinding::message)
        .containsExactly(
            "`contact` section must contain a `url`", "`contact` section must contain a `email`");
  }

  @Test
  void serversMustExistBeNonEmptyAndHaveUsableUrls() {
    assertThat(RuleFixtures.run("oas3-api-servers", HEAD))
        .extracting(OpenApiFinding::message)
        .containsExactly("no servers defined for the specification");
    assertThat(RuleFixtures.run("oas3-api-servers", HEAD + "servers: []\n"))
        .extracting(OpenApiFinding::message)
        .containsExactly("servers definition is empty, contains no servers!");
    assertThat(
            RuleFixtures.run(
                "oas3-api-servers",
                HEAD
                    + "servers:\n  - description: d\n  - url: ':x'\n  - url: 'https://a.org/v/'\n"
                    + "  - url: '?q'\n  - url: '{a}{b}'\n  - url: /\n  - url: 'https://a.org'\n"))
        .extracting(OpenApiFinding::message)
        .containsExactly(
            "server definition is missing a URL",
            "server URL cannot be parsed: parse \":x\": missing protocol scheme",
            "Server URL is not valid: must not have a trailing slash",
            "Server URL is not valid: no hostname or path provided");
  }

  @Test
  void serverUrlsAvoidExampleHostsAndTrailingSlashes() {
    final String servers =
        HEAD
            + "servers:\n  - url: https://a.example.com/v1/\n  - description: no url\n  - url: https://b.org\n";
    assertThat(RuleFixtures.positions("oas3-server-not-example-com", servers))
        .containsExactly("5:10");
    assertThat(RuleFixtures.positions("oas3-server-trailing-slash", servers))
        .containsExactly("5:10");
  }
}
