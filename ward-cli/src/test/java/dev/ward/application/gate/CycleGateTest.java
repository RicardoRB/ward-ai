package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.JvmDependencyAdapters;
import dev.ward.infrastructure.analysis.ProjectDependencyAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The cycle rule through the ward gate: a warm run with a cache gives what a cold run gives. */
class CycleGateTest {

  private static final String ROOT = "src/main/java/shop";
  private static final String ORDER = ROOT + "/orders/Order.java";
  private static final String INVOICE = ROOT + "/billing/Invoice.java";
  private static final String CYCLE = "ward:architecture/cycles";

  @TempDir Path root;

  @Test
  void aWarmRunReportsWhatAColdRunReports() throws IOException {
    writeCycle();
    final List<Finding> cold = run();

    final List<Finding> warm = run();
    deleteCache();
    final List<Finding> coldAgain = run();

    assertThat(cold).hasSize(1);
    assertThat(warm).isEqualTo(cold);
    assertThat(coldAgain).isEqualTo(cold);
  }

  @Test
  void aWarmRunDropsACycleThatAnEditBrokeInAnotherFile() throws IOException {
    writeCycle();
    assertThat(run()).hasSize(1);

    Files.writeString(root.resolve(INVOICE), "package shop.billing;\n\npublic class Invoice {}\n");

    assertThat(run()).isEmpty();
  }

  private List<Finding> run() {
    return LayerPorts.ruleFindings(
        new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
        List.of(ArchitectureGraphRules.cycles(List.of(ROOT))),
        new ProjectDependencyAdapters(List.of(new JvmDependencyAdapters())),
        CYCLE);
  }

  private void writeCycle() throws IOException {
    Files.createDirectories(root.resolve(ROOT + "/orders"));
    Files.createDirectories(root.resolve(ROOT + "/billing"));
    Files.writeString(
        root.resolve(ORDER),
        "package shop.orders;\n\nimport shop.billing.Invoice;\n\npublic class Order {\n"
            + "  Invoice invoice;\n}\n");
    Files.writeString(
        root.resolve(INVOICE),
        "package shop.billing;\n\nimport shop.orders.Order;\n\npublic class Invoice {\n"
            + "  Order order;\n}\n");
  }

  private void deleteCache() throws IOException {
    final Path cache = root.resolve(".ward");
    if (!Files.exists(cache)) {
      return;
    }
    try (Stream<Path> files = Files.walk(cache)) {
      for (final Path file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
  }
}
