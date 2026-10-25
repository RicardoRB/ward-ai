package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.QualityGateDefinitions;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every finding source reads the reports of a defined gate. */
class FindingSourceContractTest {
  @Test
  void everyFindingSourceNamesADefinedGate() {
    final CompositeFindingExtractor extractor =
        new CompositeFindingExtractor(
            List.of(
                new CheckstyleXmlFindings(),
                new PmdXmlFindings(),
                new GitleaksSarifFindings(new ForyJsonCodec()),
                new JavacOutputFindings(),
                new SpotlessOutputFindings(),
                new EslintJsonFindings(new ForyJsonCodec()),
                new TscOutputFindings(),
                new BiomeCheckstyleFindings()));

    assertThat(extractor.gates())
        .allSatisfy(
            gate ->
                assertThat(QualityGateDefinitions.find(gate))
                    .as("finding source for undefined gate %s", gate)
                    .isPresent());
  }
}
