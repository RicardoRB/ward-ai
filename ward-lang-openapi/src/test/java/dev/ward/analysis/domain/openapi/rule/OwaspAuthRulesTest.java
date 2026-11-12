package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OwaspAuthRulesTest {

  private static final String HEAD = "openapi: 3.0.3\ninfo:\n  title: T\n";

  private static String schemes(final String body) {
    return HEAD + "components:\n  securitySchemes:\n" + body;
  }

  private static List<String> messages(final String rule, final String spec) {
    return RuleFixtures.run(rule, spec).stream().map(OpenApiFinding::message).toList();
  }

  @Test
  void httpSchemeRulesLookAtTheSchemeKey() {
    final String spec =
        schemes(
            "    a:\n      type: http\n      scheme: Basic\n    b:\n      type: http\n      scheme: negotiate\n"
                + "    c:\n      type: http\n      scheme: oauth\n");
    assertThat(messages("owasp-no-http-basic", spec)).hasSize(2);
    assertThat(messages("owasp-auth-insecure-schemes", spec)).hasSize(2);
  }

  @Test
  void apiKeysAndJwtAndEndpointsAreChecked() {
    final String spec =
        schemes(
            "    k:\n      type: apiKey\n      in: path\n    j:\n      type: http\n      bearerFormat: jwt\n"
                + "    o:\n      type: openIdConnect\n      openIdConnectUrl: ftp://x\n"
                + "    f:\n      type: oauth2\n      flows:\n        implicit:\n          authorizationUrl: http://x\n"
                + "          scopes: {}\n        password:\n          tokenUrl: https://x\n          scopes: {}\n");
    assertThat(messages("owasp-no-api-keys-in-url", spec))
        .containsExactly("API keys must not be passed via URL parameters (`path`)");
    assertThat(messages("owasp-jwt-best-practices", spec))
        .containsExactly("JWT description should reference `RFC8725`");
    assertThat(messages("owasp-auth-urls-https", spec)).hasSize(2);
    assertThat(messages("owasp-oauth-no-implicit", spec)).hasSize(1);
    assertThat(messages("owasp-oauth-no-password", spec)).hasSize(1);
  }

  @Test
  void parametersAreCheckedForCredentialsAndNumericIds() {
    final String spec =
        HEAD
            + "paths:\n  /a:\n    parameters:\n      - name: refresh_token\n        in: query\n"
            + "    get:\n      parameters:\n        - name: userId\n          in: path\n          schema:\n"
            + "            type: [integer, 'null']\n        - name: ok\n          in: query\n"
            + "      responses: {}\ncomponents:\n  parameters:\n    P:\n      name: client-id\n      in: header\n"
            + "      schema:\n        type: integer\n";
    assertThat(messages("owasp-no-credentials-in-url", spec))
        .containsExactly(
            "URL parameters must not contain credentials, passwords, or secrets (`refresh_token`)");
    assertThat(messages("owasp-no-numeric-ids", spec)).hasSize(2);
  }

  @Test
  void serverUrlsAtEveryLevelMustUseTls() {
    final String spec =
        HEAD
            + "servers:\n  - url: http://a\npaths:\n  /a:\n    servers:\n      - url: ftp://b\n"
            + "    get:\n      servers:\n        - url: https://c\n        - url: http://d\n"
            + "      responses: {}\n";
    assertThat(messages("owasp-security-hosts-https-oas3", spec))
        .containsExactly(
            "server URLs should use TLS (https)",
            "server URLs should use TLS (https)",
            "server URLs should use TLS (https)");
  }
}
