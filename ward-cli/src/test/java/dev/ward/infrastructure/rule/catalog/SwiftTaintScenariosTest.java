package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The scenarios of story #605: taint support for Swift. */
class SwiftTaintScenariosTest {
  private static final String PREFIX = "ward:swift/";

  private static List<String> reported(final String ruleName, final String source) {
    final List<RuleDefinition> rules =
        CatalogRuleDefinitions.shipped().stream()
            .filter(rule -> rule.id().equals(PREFIX + ruleName))
            .toList();
    assertThat(rules).hasSize(1);
    final List<SourceUnit> units =
        List.of(new SourceUnit("Sources/App/Sample.swift", AnalysisLanguage.SWIFT, source));
    final List<AnalysisFinding> findings =
        new RuleEngine(BundledRuleMatchers.create())
            .run(units, new RuleFixtureRunner().frontends(), rules)
            .findings();
    return findings.stream().map(finding -> finding.line() + ":" + finding.column()).toList();
  }

  @Test
  @DisplayName("command injection")
  void commandInjection() {
    // Given a value from a URL query passed to Process arguments
    final String unsafe =
        """
        import Foundation

        func run(_ url: URL) throws {
          let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems
          let name = items?.first(where: { $0.name == "n" })?.value ?? ""
          let task = Process()
          task.executableURL = URL(fileURLWithPath: "/bin/sh")
          task.arguments = ["-c", "echo \\(name)"]
          try task.run()
        }
        """;
    final String sanitised =
        unsafe.replace(
            "let name = items?.first(where: { $0.name == \"n\" })?.value ?? \"\"",
            "let name = Int(items?.first(where: { $0.name == \"n\" })?.value ?? \"\") ?? 0"
        );

    // When the Swift taint rules run
    final List<String> found = reported("command-injection", unsafe);
    final List<String> clean = reported("command-injection", sanitised);

    // Then the flow is reported, and a sanitised variant is not
    assertThat(found).containsExactly("8:3");
    assertThat(clean).isEmpty();
  }

  @Test
  @DisplayName("SQL injection")
  void sqlInjection() {
    // Given user input concatenated into an SQLite statement
    final String unsafe =
        """
        import Foundation
        import SQLite3

        func find(_ url: URL, db: OpaquePointer?) {
          let name = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value ?? ""
          var statement: OpaquePointer?
          let sql = "SELECT * FROM users WHERE name = '" + name + "'"
          sqlite3_prepare_v2(db, sql, -1, &statement, nil)
        }
        """;
    final String bound =
        """
        import Foundation
        import SQLite3

        func find(_ url: URL, db: OpaquePointer?) {
          let name = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value ?? ""
          var statement: OpaquePointer?
          sqlite3_prepare_v2(db, "SELECT * FROM users WHERE name = ?", -1, &statement, nil)
          sqlite3_bind_text(statement, 1, name, -1, nil)
        }
        """;

    // When the taint rules run
    final List<String> found = reported("sql-injection", unsafe);
    final List<String> clean = reported("sql-injection", bound);

    // Then the flow is reported, and a bound parameter is not
    assertThat(found).containsExactly("8:3");
    assertThat(clean).isEmpty();
  }

  @Test
  @DisplayName("path injection")
  void pathInjection() {
    // Given user input joined into a file path that is opened
    final String unsafe =
        """
        import Foundation

        func open(_ url: URL, base: URL) -> Data? {
          let name = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value ?? ""
          let file = base.appendingPathComponent(name)
          return FileManager.default.contents(atPath: file.path)
        }
        """;
    final String validated =
        unsafe.replace(
            "appendingPathComponent(name)",
            "appendingPathComponent(URL(fileURLWithPath: name).lastPathComponent)"
        );

    // When the taint rules run
    final List<String> found = reported("path-injection", unsafe);
    final List<String> clean = reported("path-injection", validated);

    // Then the flow is reported, and a validated path is not
    assertThat(found).containsExactly("6:10");
    assertThat(clean).isEmpty();
  }
}
