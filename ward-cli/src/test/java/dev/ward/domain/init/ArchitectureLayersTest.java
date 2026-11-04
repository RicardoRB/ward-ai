package dev.ward.domain.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.project.Framework;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureLayersTest {

  @Test
  void everyStyleInitOffersMapsToItsBoundary() {
    assertThat(ArchitectureLayers.forStyle("layered", Framework.NONE))
        .isEqualTo(new LayerBoundary(List.of("controller"), List.of("repository")));
    assertThat(ArchitectureLayers.forStyle("hexagonal", Framework.NONE))
        .isEqualTo(
            new LayerBoundary(
                List.of("domain", "application"), List.of("adapter", "infrastructure")));
    assertThat(ArchitectureLayers.forStyle("clean", Framework.NONE))
        .isEqualTo(
            new LayerBoundary(
                List.of("domain", "core"), List.of("controller", "adapter", "infrastructure")));
  }

  @Test
  void anUnknownStyleUsesTheLayeredBoundary() {
    assertThat(ArchitectureLayers.forStyle("onion", Framework.NONE))
        .isEqualTo(ArchitectureLayers.forStyle("layered", Framework.NONE));
  }

  @Test
  void fastApiAddsItsRouterAndRepositoryPackages() {
    assertThat(ArchitectureLayers.forStyle("layered", Framework.FASTAPI))
        .isEqualTo(
            new LayerBoundary(
                List.of("controller", "routers", "api", "endpoints"),
                List.of("repository", "repositories", "crud")));
  }

  @Test
  void engineLanguagesIncludeSwiftWithItsTypeNameEdges() {
    assertThat(ArchitectureLayers.ENGINE_LANGUAGES)
        .contains(dev.ward.analysis.domain.ir.AnalysisLanguage.SWIFT);
  }
}
