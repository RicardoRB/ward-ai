package dev.ward.infrastructure.agent;

import dev.ward.application.agent.NativeHookProtocol;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.secrets.PromptInput;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Shared lifecycle wiring for concrete harness adapters; protocol behavior stays in each adapter.
 */
public interface NativeHarnessAdapter extends NativeHarnessLifecycle, NativeHookProtocol {
  /** Returns the harness's ID, generated files, events, and capabilities. */
  NativeAgentIntegration.Definition definition();

  /** Returns the harness configuration file content that registers the hook. */
  String configContent();

  /** Returns the hook wrapper script. */
  String hookContent();

  @Override
  default String id() {
    return definition().id();
  }

  @Override
  default Set<IntegrationCapability> capabilities() {
    return definition().capabilities();
  }

  /**
   * Returns the configuration, with the prompt hook registered when {@code prompts}. Only an adapter
   * with a prompt event overrides it.
   */
  default String configContent(final boolean prompts) {
    return configContent();
  }

  @Override
  default Optional<PromptInput> parsePrompt(final JsonCodec codec, final String payload) {
    return NativePromptEvents.parse(id(), codec, payload);
  }

  @Override
  default String blockPrompt(final String reason) {
    return NativePromptEvents.block(id(), reason);
  }

  @Override
  default NativeArtifactPlan artifacts(final Path root) {
    return NativeArtifactPlans.of(this, root, false);
  }

  @Override
  default NativeArtifactPlan artifacts(final Path root, final IntegrationRequest request) {
    return NativeArtifactPlans.of(this, root, request.promptHook() && supportsPromptHook());
  }
}
