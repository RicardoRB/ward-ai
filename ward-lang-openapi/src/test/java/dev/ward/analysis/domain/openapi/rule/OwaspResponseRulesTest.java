package dev.ward.analysis.domain.openapi.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OwaspResponseRulesTest {

  private static final String GET = "get";
  private static final String HEAD = "openapi: 3.0.3\npaths:\n  /p:\n";

  private static List<String> messages(final String rule, final String operations) {
    return RuleFixtures.run(rule, HEAD + operations).stream().map(OpenApiFinding::message).toList();
  }

  private static String op(final String method, final String extra, final String responses) {
    return "    " + method + ":\n" + extra + "      responses:\n" + responses;
  }

  @Test
  void errorResponsesAreRequiredWithASchemaAndAnonymousOperationsNeedNo401() {
    final String ok = "        '200': {description: d}\n";
    assertThat(messages("owasp-api8-2023-define-error-responses-401", op(GET, "", ok)))
        .containsExactly("missing response code `401` for `GET`");
    assertThat(
            messages(
                "owasp-api8-2023-define-error-responses-401", op(GET, "      security: []\n", ok)))
        .isEmpty();
    assertThat(
            messages(
                "owasp-api8-2023-define-error-responses-500",
                op(GET, "", "        '500': {description: d}\n")))
        .containsExactly("missing schema for `500` response on `GET`");
    assertThat(
            messages(
                "owasp-api4-2023-rate-limit-responses-429",
                op(
                    "get",
                    "",
                    "        '429':\n          description: d\n          content:\n            application/json: {}\n")))
        .containsExactly("missing schema for `429` response on `GET`");
    assertThat(messages("owasp-api8-2023-define-error-validation", op(GET, "", ok)))
        .containsExactly("missing one of `400`, `422`, `4XX` response codes");
  }

  @Test
  void rateLimitHeadersAreRequiredAndRetryAfterOnA429() {
    final String bare = "        '200':\n          description: d\n";
    assertThat(messages("owasp-api4-2023-rate-limit", op(GET, "", bare))).hasSize(1);
    assertThat(
            messages(
                "owasp-api4-2023-rate-limit",
                op(GET, "", bare + "          headers:\n            Other: {}\n")))
        .hasSize(1);
    assertThat(
            messages(
                "owasp-api4-2023-rate-limit",
                op(
                    "get",
                    "",
                    bare
                        + "          headers:\n            RateLimit-Limit: {}\n            RateLimit-Reset: {}\n")))
        .isEmpty();
    assertThat(
            messages(
                "owasp-api4-2023-rate-limit-retry-after",
                op(
                    "get",
                    "",
                    "        '429':\n          description: d\n          headers:\n            X: {}\n")))
        .containsExactly("missing 'Retry-After' header for 429 error response");
  }
}
