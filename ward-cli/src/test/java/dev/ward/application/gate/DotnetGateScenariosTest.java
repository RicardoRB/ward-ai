package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.domain.gate.LineHits;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.finding.DetectedLineCoverage;
import dev.ward.infrastructure.finding.dotnet.CoberturaXmlLineCoverage;
import dev.ward.infrastructure.finding.dotnet.StrykerNetMutationReport;
import dev.ward.infrastructure.finding.jvm.JacocoXmlLineCoverage;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.domain.gate.QualityGate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The gates of a C# project, from the issue's scenarios (#663). */
class DotnetGateScenariosTest {
  private static final int LATER_SECONDS = 60;
  private static final int MINIMUM = 80;
  private static final int MUTATION_MINIMUM = 60;
  private static final String COBERTURA =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <coverage line-rate="0.5" branch-rate="1" version="1.9" timestamp="1">
        <packages><package name="Shop" line-rate="0.5" branch-rate="1"><classes>
          <class name="Shop.Cart" filename="src/Cart.cs" line-rate="1" branch-rate="1">
            <methods/><lines>
              <line number="3" hits="2" branch="False"/><line number="4" hits="1" branch="False"/>
            </lines></class>
          <class name="Shop.Untested" filename="src/Untested.cs" line-rate="0" branch-rate="1">
            <methods/><lines>
              <line number="3" hits="0" branch="False"/><line number="4" hits="0" branch="False"/>
            </lines></class>
        </classes></package></packages>
      </coverage>
      """;
  private static final String MUTATION =
      """
      {"schemaVersion":"2","files":{"src/Cart.cs":{"language":"cs","source":"","mutants":[
        {"id":"1","mutatorName":"Arithmetic","status":"Killed",
         "location":{"start":{"line":5,"column":1},"end":{"line":5,"column":9}}},
        {"id":"2","mutatorName":"Boolean","status":"Survived",
         "location":{"start":{"line":9,"column":3},"end":{"line":9,"column":9}}}]}}}
      """;

  @TempDir Path root;

  @Test
  @DisplayName("an untested file fails coverage")
  void anUntestedFileFailsCoverage() throws IOException {
    final Path report = root.resolve("coverage.cobertura.xml");
    Files.writeString(report, COBERTURA);
    final LineCoverageReport reader =
        new DetectedLineCoverage(
            List.of(new CoberturaXmlLineCoverage(), new JacocoXmlLineCoverage())
        );
    final CoverageGateFixture fixture = new CoverageGateFixture();
    fixture.report(
        CoverageGateFixture.REPORT,
        CoverageGateFixture.NEW,
        true,
        reader.read(report).entries().toArray(LineHits.Entry[]::new)
    );

    final GateResult result = fixture.run(MINIMUM, null, null);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).isEqualTo("Line coverage 50% is below the minimum of 80%.");
    assertThat(reader.read(report).entries())
        .extracting(LineHits.Entry::name)
        .containsExactly("src/Cart.cs", "src/Untested.cs");
  }

  @Test
  @DisplayName("a surviving mutant")
  void aSurvivingMutant() throws IOException {
    final Path file = root.resolve(StrykerNetMutationReport.REPORT);
    Files.createDirectories(file.getParent());
    Files.writeString(file, MUTATION);
    // The tool writes the report while the gate runs; stand in for that with a later time.
    Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(LATER_SECONDS)));
    final QualityGate command =
        new CommandQualityGate(
            "stryker-net",
            new FakeCommandExecutor(0, "", ""),
            List.of("dotnet", "stryker"),
            ReportDirectories.NONE
        );
    final QualityGate gate =
        new MutationScoreGate(
            command,
            new StrykerNetMutationReport(new ForyJsonCodec()),
            MUTATION_MINIMUM,
            "hint"
        );

    final GateResult result =
        gate.execute(new Project(root, Language.CSHARP, BuildTool.DOTNET, Framework.NONE));

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary())
        .contains("Mutation score 50% is below the minimum of 60%")
        .contains("src/Cart.cs:9 Boolean");
  }
}
