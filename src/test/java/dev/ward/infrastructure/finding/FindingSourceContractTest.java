package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.QualityGateDefinitions;
import dev.ward.domain.project.WorkspaceMembers;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every finding source reads the reports of a defined gate. */
class FindingSourceContractTest {
  @Test
  void everyFindingSourceNamesADefinedGate() {
    final CompositeFindingExtractor extractor =
        new CompositeFindingExtractor(
            Stream.concat(
                    Stream.of(
                        new CheckstyleXmlFindings(),
                        new PmdXmlFindings(),
                        new GitleaksSarifFindings(new ForyJsonCodec()),
                        new JavacOutputFindings(),
                        new SpotlessOutputFindings(),
                        new EslintJsonFindings(new ForyJsonCodec()),
                        new TscOutputFindings(),
                        new BiomeCheckstyleFindings(),
                        new RuffJsonFindings(new ForyJsonCodec()),
                        new MypyJsonFindings(new ForyJsonCodec())),
                    new SwiftFindingSources(
                            new SwiftLintJsonFindings(new ForyJsonCodec()),
                            new SwiftFormatOutputFindings())
                        .sources().stream())
                .toList());

    assertThat(extractor.gates())
        .contains("swiftlint", "swift-format", "swift-build", "xcodebuild-test")
        .allSatisfy(
            gate ->
                assertThat(QualityGateDefinitions.find(gate))
                    .as("finding source for undefined gate %s", gate)
                    .isPresent());
    assertThat(new TurborepoFindings(new TscOutputFindings(), WorkspaceMembers.none()).gateId())
        .as("the turbo wrapper names its delegate's gate")
        .isEqualTo("tsc");
  }
}
