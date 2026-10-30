package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Textual script insertion and replacement in package.json that keeps every other byte. */
class PackageJsonScriptEditTest {
  @Test
  void replacesAnExistingScriptAndKeepsEveryOtherByte() {
    final String original =
        "{\n  \"name\": \"shop\",\n  \"scripts\": {\n    \"lint\": \"eslint .\",\n"
            + "    \"build\": \"tsc\"\n  }\n}\n";

    assertThat(PackageJsonEdit.withScript(original, "lint", "eslint . --format json"))
        .isEqualTo(
            "{\n  \"name\": \"shop\",\n  \"scripts\": {\n    \"lint\": \"eslint . --format json\",\n"
                + "    \"build\": \"tsc\"\n  }\n}\n");
  }

  @Test
  void insertsAMissingScriptAfterTheLastOne() {
    final String original = "{\n  \"scripts\": {\n    \"build\": \"tsc\"\n  }\n}\n";

    assertThat(PackageJsonEdit.withScript(original, "check-types", "tsc --noEmit --pretty false"))
        .isEqualTo(
            "{\n  \"scripts\": {\n    \"build\": \"tsc\",\n"
                + "    \"check-types\": \"tsc --noEmit --pretty false\"\n  }\n}\n");
  }

  @Test
  void createsScriptsWhenAbsent() {
    final String original = "{\n  \"name\": \"shop\"\n}\n";

    assertThat(PackageJsonEdit.withScript(original, "test", "vitest run"))
        .isEqualTo(
            "{\n  \"name\": \"shop\",\n  \"scripts\": {\n    \"test\": \"vitest run\"\n  }\n}\n");
  }

  @Test
  void scriptEditKeepsCrlfLineEndings() {
    final String original = "{\r\n  \"scripts\": {\r\n    \"lint\": \"eslint .\"\r\n  }\r\n}\r\n";

    assertThat(PackageJsonEdit.withScript(original, "lint", "eslint . --format json"))
        .isEqualTo(
            "{\r\n  \"scripts\": {\r\n    \"lint\": \"eslint . --format json\"\r\n  }\r\n}\r\n");
  }

  @Test
  void scriptValueQuotesAreEscaped() {
    final String original = "{\n  \"scripts\": {\n    \"test\": \"jest\"\n  }\n}\n";

    assertThat(
            PackageJsonEdit.withScript(
                original, "test", "jest --coverageThreshold '{\"global\":1}'"))
        .contains("\"test\": \"jest --coverageThreshold '{\\\"global\\\":1}'\"");
  }

  @Test
  void scriptKeyInsideAnotherValueIsNotMatched() {
    final String original =
        "{\n  \"scripts\": {\n    \"echo\": \"echo \\\"lint\\\": x\",\n"
            + "    \"build\": \"tsc\"\n  }\n}\n";

    assertThat(PackageJsonEdit.withScript(original, "lint", "eslint ."))
        .contains("\"build\": \"tsc\",\n    \"lint\": \"eslint .\"\n  }");
  }
}
