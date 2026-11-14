package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.port.ControlFlowResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the flow sites that the Java lowering records on each block. */
class JavaFlowSitesTest {

  private static final int CALL_LINE = 7;

  private static final String DB =
      """
      import java.sql.Connection;
      import java.sql.Statement;

      class Db {
        void run(Connection connection, String sql, @Untrusted String input) throws Exception {
          Statement st = connection.createStatement();
          st.executeQuery(sql);
          log(input.length());
          missing.call();
        }
      }
      """;

  /** Scenario: Java call on a declared variable. */
  @Test
  void callOnDeclaredVariableCarriesReceiverType() {
    final List<FlowSite> sites = sitesOf(DB, "run(3)", FlowSiteKind.CALL, "executeQuery");
    assertThat(sites).hasSize(1);
    final FlowSite call = sites.get(0);
    assertThat(call.qualifier()).isEqualTo("st");
    assertThat(call.receiverType()).contains("java.sql.Statement");
    assertThat(call.arguments()).hasSize(1);
    assertThat(call.arguments().get(0).startLine()).isEqualTo(CALL_LINE);
  }

  /** A qualifier that is neither declared nor a type name has no receiver type. */
  @Test
  void undeclaredQualifierHasNoReceiverType() {
    final List<FlowSite> sites = sitesOf(DB, "run(3)", FlowSiteKind.CALL, "call");
    assertThat(sites).hasSize(1);
    assertThat(sites.get(0).qualifier()).isEqualTo("missing");
    assertThat(sites.get(0).receiverType()).isEmpty();
  }

  /** A declared String parameter resolves to java.lang.String. */
  @Test
  void declaredParameterResolvesThroughJavaLang() {
    final List<FlowSite> sites = sitesOf(DB, "run(3)", FlowSiteKind.CALL, "length");
    assertThat(sites).hasSize(1);
    assertThat(sites.get(0).receiverType()).contains("java.lang.String");
  }

  /** Inner calls are listed before the calls that contain them. */
  @Test
  void innerCallsComeFirst() {
    final List<FlowSite> sites = sitesOf(DB, "run(3)", FlowSiteKind.CALL, "");
    assertThat(sites.stream().map(FlowSite::name).toList())
        .containsSubsequence("createStatement", "executeQuery");
  }

  /** Annotated parameters carry their annotation names. */
  @Test
  void annotatedParameterIsASite() {
    final List<FlowSite> sites = sitesOf(DB, "run(3)", FlowSiteKind.PARAMETER, "input");
    assertThat(sites).hasSize(1);
    assertThat(sites.get(0).annotations()).containsExactly("Untrusted");
  }

  private static final String CREATED =
      """
      class Router {
        private final Base fixed = new Impl();
        private Base held;

        void run(boolean flag, Impl impl) {
          Base once = new Impl();
          once.go();
          Base twice;
          if (flag) { twice = new Impl(); } else { twice = new Other(); }
          twice.go();
          fixed.go();
          held.go();
          (new Impl()).go();
          impl.go();
          Base anonymous = new Base() { };
          anonymous.go();
        }
      }
      """;

  /** A receiver created as one class names it; two classes, fields and anonymous classes do not. */
  @Test
  void receiverCreatedAsOneClassIsMarked() {
    final List<FlowSite> calls = sitesOf(CREATED, "run(2)", FlowSiteKind.CALL, "go");
    assertThat(calls.stream().map(FlowSite::qualifier).toList())
        .containsExactly("once", "twice", "fixed", "held", "(newImpl())", "impl", "anonymous");
    final String impl = JavaCreationTypes.MARK + "Impl";
    assertThat(calls.stream().map(FlowSite::annotations).toList())
        .containsExactly(
            List.of(impl),
            List.of(),
            List.of(impl),
            List.of(),
            List.of(impl),
            List.of(),
            List.of());
    assertThat(calls.get(3).receiverType()).contains("Base");
    assertThat(calls.get(5).receiverType()).contains("Impl");
  }

  private static List<FlowSite> sitesOf(
      final String source, final String body, final FlowSiteKind kind, final String name) {
    final ControlFlowResult result = JavaFlowFixtures.lower(source);
    final ControlFlowGraph graph =
        result.graphs().stream()
            .filter(g -> g.body().endsWith("#" + body))
            .findFirst()
            .orElseThrow();
    return graph.blocks().stream()
        .map(BasicBlock::sites)
        .flatMap(List::stream)
        .filter(site -> site.kind() == kind)
        .filter(site -> name.isEmpty() || site.name().equals(name))
        .toList();
  }
}
