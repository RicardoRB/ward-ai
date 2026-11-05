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

  private static final String CONSTRUCTOR = "<init>";
  private static final int CREATION_SITES = 5;

  private static final String CREATIONS =
      """
      import java.io.File;

      class Creations {
        void run(String name) {
          File file = new File(name);
          new java.io.FileReader(file.getPath());
          Object o = new Object() { int x = 1; };
          Object p = new File(new String(name).trim());
        }
      }
      """;

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

  /** Scenario: constructor site with an imported type, carrying its argument. */
  @Test
  void constructorSiteResolvesImportedType() {
    final List<FlowSite> sites = sitesOf(CREATIONS, "run(1)", FlowSiteKind.CALL, CONSTRUCTOR);
    assertThat(sites).hasSize(CREATION_SITES);
    final FlowSite file = sites.get(0);
    assertThat(file.qualifier()).isEqualTo("File");
    assertThat(file.receiverType()).contains("java.io.File");
    assertThat(file.arguments()).hasSize(1);
  }

  /** A fully qualified type is used as written, with no import. */
  @Test
  void constructorSiteKeepsQualifiedTypeName() {
    final List<FlowSite> sites = sitesOf(CREATIONS, "run(1)", FlowSiteKind.CALL, CONSTRUCTOR);
    assertThat(sites.get(1).qualifier()).isEqualTo("java.io.FileReader");
    assertThat(sites.get(1).receiverType()).contains("java.io.FileReader");
  }

  /** An anonymous class body records no site, but its creation still does. */
  @Test
  void anonymousClassRecordsOnlyTheCreation() {
    final List<FlowSite> sites = sitesOf(CREATIONS, "run(1)", FlowSiteKind.CALL, CONSTRUCTOR);
    assertThat(sites.get(2).receiverType()).contains("java.lang.Object");
    assertThat(sites.get(2).arguments()).isEmpty();
    assertThat(sites).hasSize(CREATION_SITES);
  }

  /** The arguments of a creation are listed before the creation that holds them. */
  @Test
  void nestedCreationComesAfterItsArguments() {
    final List<FlowSite> sites = sitesOf(CREATIONS, "run(1)", FlowSiteKind.CALL, "");
    assertThat(sites.stream().map(FlowSite::name).toList())
        .containsExactly(
            CONSTRUCTOR, "getPath", CONSTRUCTOR, CONSTRUCTOR, CONSTRUCTOR, "trim", CONSTRUCTOR);
  }

  /** A constructor's {@code super(...)} call is a site named by its keyword, with its argument. */
  @Test
  void superConstructorCallIsASiteNamedByItsKeyword() {
    final String source =
        "class Failure extends RuntimeException {\n"
            + "  Failure(String message) {\n"
            + "    super(message);\n"
            + "  }\n"
            + "}\n";
    final List<FlowSite> sites =
        JavaFlowFixtures.lower(source).graphs().stream()
            .flatMap(graph -> graph.blocks().stream())
            .flatMap(block -> block.sites().stream())
            .filter(site -> site.kind() == FlowSiteKind.CALL)
            .toList();
    assertThat(sites).hasSize(1);
    assertThat(sites.get(0).name()).isEqualTo("super");
    assertThat(sites.get(0).arguments()).hasSize(1);
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
