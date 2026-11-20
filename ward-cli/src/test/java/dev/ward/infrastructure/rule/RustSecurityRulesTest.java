package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.FlowStep;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The bundled Rust taint rules, on the scenarios of story R9. */
class RustSecurityRulesTest {
  private static final int UNSAFE_LINE = 6;
  private static final String PROGRAM = """
      use std::process::Command;

      fn main() {
          let name = std::env::args().nth(1).unwrap();
          run(name);
      }

      fn run(name: String) {
          launch(name);
      }

      fn launch(%s: String) {
          Command::new("ls").arg(%s).status().unwrap();
      }
      """;

  @Test
  @DisplayName("an injection across functions")
  void anInjectionAcrossFunctions() {
    final List<AnalysisFinding> found =
        RustSecurityRules.run(PROGRAM.formatted("target", "target"));
    assertThat(found).hasSize(1);
    assertThat(found.get(0).ruleId()).isEqualTo("ward:rust/command-injection");
    assertThat(found.get(0).path().stream().map(FlowStep::message).toList())
        .first()
        .isEqualTo("source: args");
  }

  @Test
  @DisplayName("a sanitized value")
  void aSanitizedValue() {
    assertThat(
        RustSecurityRules.run(
            PROGRAM.replace(
                "let name = std::env::args().nth(1).unwrap();",
                "let name = std::env::args().nth(1).unwrap().parse::<u32>().unwrap().to_string();"
            )
                .formatted("target", "target")
        )
    ).isEmpty();
  }

  @Test
  @DisplayName("SQL built with format! from the command line")
  void sqlBuiltWithFormat() {
    assertThat(RustSecurityRules.run("""
            fn main(conn: &Connection) {
                let id = std::env::args().nth(1).unwrap();
                let sql = format!("SELECT * FROM users WHERE id = {}", id);
                conn.execute(&sql, []).unwrap();
            }
            """)).extracting(AnalysisFinding::ruleId).containsExactly("ward:rust/sql-injection");
  }

  @Test
  @DisplayName("a path taken from the environment")
  void pathFromTheEnvironment() {
    assertThat(RustSecurityRules.run("""
            use std::fs;
            use std::path::Path;

            fn main() {
                let name = std::env::var("NAME").unwrap();
                let text = fs::read_to_string(Path::new("/srv").join(&name)).unwrap();
                let safe = Path::new(&name).file_name().unwrap();
                fs::read_to_string(Path::new("/srv").join(safe)).unwrap();
            }
            """)).extracting(AnalysisFinding::line).containsExactly(UNSAFE_LINE);
  }
}
