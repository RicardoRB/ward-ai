package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Textual dev dependency insertion that leaves every other byte of package.json unchanged. */
class PackageJsonEditTest {
  private static final Map<String, String> ESLINT =
      ordered("eslint", "10.12.0", "@eslint/js", "10.0.1");

  @Test
  void appendsToTwoSpaceDevDependencies() {
    final String original =
        """
        {
          "name": "shop",
          "devDependencies": {
            "typescript": "^5.9.3"
          }
        }
        """;

    assertThat(PackageJsonEdit.addDevDependencies(original, ESLINT))
        .isEqualTo(
            """
            {
              "name": "shop",
              "devDependencies": {
                "typescript": "^5.9.3",
                "eslint": "10.12.0",
                "@eslint/js": "10.0.1"
              }
            }
            """);
  }

  @Test
  void keepsTabIndentation() {
    final String original =
        "{\n\t\"name\": \"shop\",\n\t\"devDependencies\": {\n\t\t\"tsx\": \"4.0.0\"\n\t}\n}\n";

    assertThat(PackageJsonEdit.addDevDependencies(original, ordered("prettier", "3.9.10")))
        .isEqualTo(
            "{\n\t\"name\": \"shop\",\n\t\"devDependencies\": {\n\t\t\"tsx\": \"4.0.0\",\n"
                + "\t\t\"prettier\": \"3.9.10\"\n\t}\n}\n");
  }

  @Test
  void createsMissingDevDependencies() {
    final String original =
        """
        {
          "name": "shop",
          "dependencies": {
            "zod": "^3.23.0"
          }
        }
        """;

    assertThat(PackageJsonEdit.addDevDependencies(original, ordered("prettier", "3.9.10")))
        .isEqualTo(
            """
            {
              "name": "shop",
              "dependencies": {
                "zod": "^3.23.0"
              },
              "devDependencies": {
                "prettier": "3.9.10"
              }
            }
            """);
  }

  @Test
  void fillsAnEmptyDevDependenciesObject() {
    final String original = "{\n  \"name\": \"shop\",\n  \"devDependencies\": {}\n}\n";

    assertThat(PackageJsonEdit.addDevDependencies(original, ordered("prettier", "3.9.10")))
        .isEqualTo(
            "{\n  \"name\": \"shop\",\n  \"devDependencies\": {\n    \"prettier\": \"3.9.10\"\n"
                + "  }\n}\n");
  }

  @Test
  void ignoresNestedDevDependenciesKeys() {
    final String original =
        """
        {
          "config": {"devDependencies": {"a": "1"}},
          "name": "shop"
        }
        """;

    assertThat(PackageJsonEdit.addDevDependencies(original, ordered("prettier", "3.9.10")))
        .endsWith(
            """
              "name": "shop",
              "devDependencies": {
                "prettier": "3.9.10"
              }
            }
            """);
  }

  @Test
  void minifiedFileFailsWithoutGuessing() {
    assertThatThrownBy(
            () ->
                PackageJsonEdit.addDevDependencies(
                    "{\"name\":\"shop\",\"devDependencies\":{}}", ordered("prettier", "3.9.10")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("layout");
  }

  private static Map<String, String> ordered(final String... pairs) {
    final Map<String, String> entries = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      entries.put(pairs[i], pairs[i + 1]);
    }
    return entries;
  }
}
