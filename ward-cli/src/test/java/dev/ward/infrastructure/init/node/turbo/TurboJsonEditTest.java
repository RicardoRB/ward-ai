package dev.ward.infrastructure.init.node.turbo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Turborepo task insertion into turbo.json text that keeps every other byte unchanged. */
class TurboJsonEditTest {
  private static final String CREATE_TURBO =
      """
      {
        "$schema": "https://turborepo.com/schema.json",
        "ui": "tui",
        "tasks": {
          "build": {
            "dependsOn": ["^build"],
            "outputs": [".next/**"]
          },
          "dev": {
            "cache": false,
            "persistent": true
          }
        }
      }
      """;
  private static final Map<String, String> CHECK_TYPES =
      tasks("check-types", "{\"dependsOn\": [\"^build\"]}");

  @Test
  void insertsAMissingTaskAndKeepsTheExistingOnes() {
    assertThat(TurboJsonEdit.addTasks(CREATE_TURBO, CHECK_TYPES))
        .isEqualTo(
            """
            {
              "$schema": "https://turborepo.com/schema.json",
              "ui": "tui",
              "tasks": {
                "build": {
                  "dependsOn": ["^build"],
                  "outputs": [".next/**"]
                },
                "dev": {
                  "cache": false,
                  "persistent": true
                },
                "check-types": {"dependsOn": ["^build"]}
              }
            }
            """);
  }

  @Test
  void existingTaskKeepsItsSettings() {
    final String edited =
        TurboJsonEdit.addTasks(CREATE_TURBO, tasks("build", "{\"outputs\": [\"coverage/**\"]}"));

    assertThat(edited).isEqualTo(CREATE_TURBO);
  }

  @Test
  void commentsAreNotReadAsTasks() {
    final String commented =
        """
        {
          // "lint" is written by hand, not by Ward
          "tasks": {
            /* "test": { "cache": false } */
            "build": {"outputs": ["dist/**"]}
          }
        }
        """;

    final String edited = TurboJsonEdit.addTasks(commented, tasks("test", "{\"cache\": true}"));

    assertThat(edited)
        .contains("// \"lint\" is written by hand, not by Ward")
        .contains("/* \"test\": { \"cache\": false } */")
        .contains("\"build\": {\"outputs\": [\"dist/**\"]},\n    \"test\": {\"cache\": true}\n");
  }

  @Test
  void minifiedFileFailsWithoutGuessing() {
    assertThatThrownBy(() -> TurboJsonEdit.addTasks("{\"tasks\":{}}", CHECK_TYPES))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("layout");
  }

  private static Map<String, String> tasks(final String... pairs) {
    final Map<String, String> entries = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      entries.put(pairs[i], pairs[i + 1]);
    }
    return entries;
  }
}
