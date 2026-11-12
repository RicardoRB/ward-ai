package dev.ward.application.agent;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationServiceTest {
  private final List<Boolean> removals = new ArrayList<>();
  private final AgentIntegration legacy =
      new AgentIntegration() {
        @Override
        public List<PlannedFileChange> plan(final Path projectRoot) {
          return List.of();
        }

        @Override
        public String id() {
          return "legacy";
        }

        @Override
        public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
          removals.add(remove);
          return new IntegrationResult(IntegrationResult.Status.CHANGED, List.of(), "ok");
        }
      };

  @Test
  void requestReachesTheIntegrationAndLegacyIntegrationsUseTheFlag(@TempDir final Path root) {
    withPom(root, PLAIN_POM);
    final List<IntegrationRequest> requests = new ArrayList<>();
    final AgentIntegration recording =
        new AgentIntegration() {
          @Override
          public List<PlannedFileChange> plan(final Path projectRoot) {
            return List.of();
          }

          @Override
          public String id() {
            return "recording";
          }

          @Override
          public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
            return integrate(projectRoot, new IntegrationRequest(remove));
          }

          @Override
          public IntegrationResult integrate(
              final Path projectRoot, final IntegrationRequest request) {
            requests.add(request);
            return new IntegrationResult(IntegrationResult.Status.CHANGED, List.of(), "ok");
          }
        };
    final IntegrationService service =
        new IntegrationService(
            new ProjectDetector(new FileSystemProjectInspection()), List.of(recording, legacy));

    service.integrate("recording", root, IntegrationRequest.INSTALL);
    service.integrate("recording", root, true);
    service.integrate("legacy", root, IntegrationRequest.INSTALL);

    assertThat(requests).containsExactly(IntegrationRequest.INSTALL, IntegrationRequest.REMOVE);
    assertThat(removals).containsExactly(false);
  }
}
