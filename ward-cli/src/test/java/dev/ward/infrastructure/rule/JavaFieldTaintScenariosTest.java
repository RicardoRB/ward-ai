package dev.ward.infrastructure.rule;

import static dev.ward.infrastructure.rule.JavaSecurityRuleRun.IMPORTS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Story #603: a tainted value that one method stores in a field and another reads. */
final class JavaFieldTaintScenariosTest {
  private static final String RULE = "ward:java/sql-injection";
  private static final String HEAD =
      "class Search {\n  private String data;\n  private String other;\n";
  private static final String WRITER =
      "  void bad(HttpServletRequest request, Statement st) throws Exception {\n"
          + "    String id = request.getParameter(\"id\");\n";
  private static final String READER_HEAD = "  void sink(Statement st) throws Exception {\n";

  private static List<AnalysisFinding> run(final String body) {
    return JavaSecurityRuleRun.findings(RULE, IMPORTS + HEAD + body + "}\n");
  }

  @Test
  @DisplayName("a flow through a private instance field")
  void aFlowThroughAPrivateInstanceField() {
    // Given a private field written from a source in one method and read into a sink in another
    final String body =
        WRITER + "    data = id;\n    sink(st);\n  }\n"
            + READER_HEAD + "    String d = data;\n    st.executeQuery(\"select \" + d);\n  }\n";
    // When the Java taint rules run
    final List<AnalysisFinding> found = run(body);
    // Then the sink is reported
    assertThat(found).hasSize(1);
  }

  @Test
  @DisplayName("a sanitised value stored in a field")
  void aSanitisedValueStoredInAField() {
    // Given the same flow with the value sanitised before it is stored
    final String body =
        WRITER + "    data = String.valueOf(Integer.parseInt(id));\n    sink(st);\n  }\n"
            + READER_HEAD + "    String d = data;\n    st.executeQuery(\"select \" + d);\n  }\n";
    // When the Java taint rules run
    // Then nothing is reported
    assertThat(run(body)).isEmpty();
  }

  @Test
  @DisplayName("separate fields")
  void separateFields() {
    // Given a tainted value stored in one field and a different field read into the sink
    final String body =
        WRITER + "    data = id;\n    other = \"x\";\n    sink(st);\n  }\n"
            + READER_HEAD + "    String d = other;\n    st.executeQuery(\"select \" + d);\n  }\n";
    // When the Java taint rules run
    // Then nothing is reported
    assertThat(run(body)).isEmpty();
  }

  @Test
  @DisplayName("a local variable named like a field")
  void aLocalVariableNamedLikeAField() {
    // Given a method whose local variable has the name of the tainted field
    final String body =
        WRITER + "    data = id;\n    sink(st);\n  }\n"
            + READER_HEAD + "    String data = \"x\";\n    st.executeQuery(\"select \" + data);\n  }\n";
    // When the Java taint rules run
    // Then nothing is reported
    assertThat(run(body)).isEmpty();
  }
}
