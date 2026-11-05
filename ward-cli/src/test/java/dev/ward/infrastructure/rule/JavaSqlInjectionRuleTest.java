package dev.ward.infrastructure.rule;

import static dev.ward.infrastructure.rule.JavaSecurityRuleRun.IMPORTS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Scenarios of {@code ward:java/sql-injection}, run through the rule engine with full paths. */
final class JavaSqlInjectionRuleTest {

  private static final String RULE = "ward:java/sql-injection";

  /**
   * Scenario: SQL injection from a servlet parameter. The source call is the write of id, so the
   * path has the source and the sink only.
   */
  @Test
  void servletParameterReachesExecuteQuery() {
    final List<AnalysisFinding> found =
        JavaSecurityRuleRun.findings(
            RULE,
            IMPORTS
                + """
                class Search {
                  void run(HttpServletRequest request, Statement statement) throws Exception {
                    String id = request.getParameter("id");
                    statement.executeQuery("select * from t where id = " + id);
                  }
                }
                """);
    assertThat(found).hasSize(1);
    assertThat(JavaTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: getParameter", "sink: executeQuery argument 0");
  }

  /** A value carried by a later assignment adds its step to the path, in order. */
  @Test
  void propagatedValueHasAnAssignmentStep() {
    final List<AnalysisFinding> found =
        JavaSecurityRuleRun.findings(
            RULE,
            IMPORTS
                + """
                class Search {
                  void run(HttpServletRequest request, Statement statement) throws Exception {
                    String a = request.getParameter("id");
                    String b = "x" + a;
                    statement.executeQuery(b);
                  }
                }
                """);
    assertThat(found).hasSize(1);
    assertThat(JavaTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: getParameter", "assigned to b", "sink: executeQuery argument 0");
  }

  /** Scenario: parameterized query. The tainted value is bound, not part of the SQL text. */
  @Test
  void parameterizedQueryIsNotReported() {
    final List<AnalysisFinding> found =
        JavaSecurityRuleRun.findings(
            RULE,
            IMPORTS
                + """
                class Search {
                  void run(HttpServletRequest request, Connection connection) throws Exception {
                    String id = request.getParameter("id");
                    var ps = connection.prepareStatement("select * from t where id = ?");
                    ps.setString(1, id);
                  }
                }
                """);
    assertThat(found).isEmpty();
  }

  /** Scenario: a receiver that is not a JDBC type is not matched, even with a tainted argument. */
  @Test
  void unrelatedReceiverIsNotMatched() {
    final List<AnalysisFinding> found =
        JavaSecurityRuleRun.findings(
            RULE,
            IMPORTS
                + """
                class Search {
                  void run(HttpServletRequest request, Other other) {
                    String id = request.getParameter("id");
                    other.executeQuery(id);
                  }
                }
                """);
    assertThat(found).isEmpty();
  }

  /** A sanitizer's result is clean, so a parsed integer is not reported. */
  @Test
  void parsedIntegerIsNotReported() {
    final List<AnalysisFinding> found =
        JavaSecurityRuleRun.findings(
            RULE,
            IMPORTS
                + """
                class Search {
                  void run(HttpServletRequest request, Statement statement) throws Exception {
                    int id = Integer.parseInt(request.getParameter("id"));
                    statement.executeQuery("select * from t where id = " + id);
                  }
                }
                """);
    assertThat(found).isEmpty();
  }

  /** The jakarta namespace has the same servlet getters. */
  @Test
  void jakartaRequestReachesExecute() {
    final List<AnalysisFinding> found =
        JavaSecurityRuleRun.findings(
            RULE,
            """
            import java.sql.Statement;
            import jakarta.servlet.http.HttpServletRequest;
            class Search {
              void run(HttpServletRequest request, Statement statement) throws Exception {
                String id = request.getHeader("id");
                statement.execute(id);
              }
            }
            """);
    assertThat(found).hasSize(1);
  }
}
