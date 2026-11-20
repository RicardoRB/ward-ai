package dev.ward.analysis.domain.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class CSharpFlowSitesTest {
  private static final String SOURCE =
      """
      class C {
        void Run(string id) {
          var name = Request.Query["n"];
          var command = new SqlCommand(id);
          command.CommandText = name;
          Helper.Go(name, 1);
          Other(name);
        }
      }
      """;

  private static ControlFlowGraph graph() {
    final SourceUnit unit = new SourceUnit("m.cs", AnalysisLanguage.CSHARP, SOURCE);
    return new CSharpControlFlow()
        .lower(unit, new AntlrLanguageFrontend().parse(unit).tree())
        .graphs()
        .get(0);
  }

  private static List<FlowSite> sites() {
    return graph().blocks().stream().map(BasicBlock::sites).flatMap(List::stream).toList();
  }

  @Test
  void aQueryStringReadIsAMemberReadOfItsReceiver() {
    assertThat(sites())
        .anySatisfy(
            site -> {
              assertThat(site.kind()).isEqualTo(FlowSiteKind.MEMBER_READ);
              assertThat(site.name()).isEqualTo("Query");
              assertThat(site.qualifier()).isEqualTo("Request");
            }
        );
  }

  @Test
  void aCreationIsACallNamedByItsTypeWithItsArguments() {
    assertThat(sites())
        .anySatisfy(
            site -> {
              assertThat(site.kind()).isEqualTo(FlowSiteKind.CALL);
              assertThat(site.name()).isEqualTo("SqlCommand");
              assertThat(site.arguments()).hasSize(1);
            }
        );
  }

  @Test
  void anAssignedMemberOfATypedLocalIsAWriteWithItsReceiverType() {
    assertThat(sites())
        .anySatisfy(
            site -> {
              assertThat(site.kind()).isEqualTo(FlowSiteKind.MEMBER_WRITE);
              assertThat(site.name()).isEqualTo("CommandText");
              assertThat(site.receiverType()).contains("SqlCommand");
            }
        );
  }

  @Test
  void aStaticCallNamesItsTypeAndAnUnqualifiedCallHasNoQualifier() {
    assertThat(sites())
        .anySatisfy(
            site -> {
              assertThat(site.name()).isEqualTo("Go");
              assertThat(site.receiverType()).contains("Helper");
              assertThat(site.arguments()).hasSize(2);
            }
        )
        .anySatisfy(
            site -> {
              assertThat(site.name()).isEqualTo("Other");
              assertThat(site.qualifier()).isEmpty();
            }
        );
  }

  @Test
  void theParameterIsBoundAtTheEntryAndLocalsAreWrittenOnDeclaration() {
    final List<VariableAccess> accesses =
        graph().blocks().stream().map(BasicBlock::accesses).flatMap(List::stream).toList();

    assertThat(accesses)
        .anySatisfy(
            access -> {
              assertThat(access.kind()).isEqualTo(AccessKind.PARAMETER);
              assertThat(access.name()).isEqualTo("id");
            }
        )
        .anySatisfy(
            access -> {
              assertThat(access.kind()).isEqualTo(AccessKind.WRITE);
              assertThat(access.name()).isEqualTo("name");
            }
        );
  }
}
