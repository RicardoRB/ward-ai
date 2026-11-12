package dev.ward.config;

import static dev.ward.ProjectSnapshot.assertPlanMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectSnapshot;
import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every registered integration's plan matches what installing it writes, in every state. */
class IntegrationPlanContractTest {
  @TempDir Path root;

  static Stream<Arguments> integrations() {
    return WardProvidesModule.agentIntegrations().stream()
        .sorted(Comparator.comparing(AgentIntegration::id))
        .map(integration -> Arguments.of(integration.id(), integration));
  }

  @ParameterizedTest(name = "{0} on an empty project")
  @MethodSource("integrations")
  void installMatchesPlan(final String id, final AgentIntegration integration) {
    assertPlanMatches(root, () -> integration.plan(root), () -> install(integration));
  }

  @ParameterizedTest(name = "{0} after an install")
  @MethodSource("integrations")
  void refreshMatchesPlan(final String id, final AgentIntegration integration) {
    install(integration);

    assertPlanMatches(root, () -> integration.plan(root), () -> install(integration));
  }

  @ParameterizedTest(name = "{0} with one installed file missing")
  @MethodSource("integrations")
  void partialInstallMatchesPlan(final String id, final AgentIntegration integration)
      throws IOException {
    final Map<String, String> before = ProjectSnapshot.of(root);
    install(integration);
    final String created =
        ProjectSnapshot.of(root).keySet().stream()
            .filter(file -> !before.containsKey(file))
            .findFirst()
            .orElseThrow();
    Files.delete(root.resolve(created));

    assertPlanMatches(root, () -> integration.plan(root), () -> install(integration));
  }

  @ParameterizedTest(name = "{0} with a user-owned file in the way")
  @MethodSource("integrations")
  void conflictIsPlannedAndNothingIsWritten(final String id, final AgentIntegration integration)
      throws IOException {
    final Path target = integration.plan(root).getFirst().file();
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user-owned\n");

    final List<PlannedFileChange> plan = integration.plan(root);
    final Map<String, String> before = ProjectSnapshot.of(root);
    install(integration);

    assertThat(plan)
        .extracting(PlannedFileChange::action)
        .contains(PlannedFileChange.Action.CONFLICT);
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  private void install(final AgentIntegration integration) {
    integration.integrate(root, IntegrationRequest.INSTALL);
  }
}
