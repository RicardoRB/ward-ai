package dev.ward.infrastructure.analysis.node;

import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import dev.ward.infrastructure.analysis.ProcessTypeSidecar;
import dev.ward.infrastructure.analysis.SidecarScripts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The TypeScript type sidecar: {@code ward-ts-types.mjs} run by {@code node}, loading the project's
 * own {@code typescript} package and its {@code tsconfig.json}.
 */
public final class TypeScriptTypeSidecar implements TypeSidecar {
  private static final String SCRIPT = "ward-ts-types.mjs";
  private static final String LANGUAGE = "typescript";

  private final Path projectRoot;
  private final String wardVersion;
  private ProcessTypeSidecar running;

  /**
   * Creates the sidecar for the project at {@code projectRoot}, cached under {@code wardVersion}.
   */
  public TypeScriptTypeSidecar(final Path projectRoot, final String wardVersion) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
    this.wardVersion = wardVersion;
  }

  @Override
  public Optional<TypeFacts> start() {
    final Optional<Path> script = SidecarScripts.extract(projectRoot, wardVersion, SCRIPT);
    if (script.isEmpty()) {
      return Optional.empty();
    }
    running =
        new ProcessTypeSidecar(
            LANGUAGE,
            List.of("node", script.get().toString(), projectRoot.toString()),
            projectRoot);
    return running.start().map(facts -> new CachedTypeFacts(facts, configurationHash()));
  }

  /** The hash of {@code tsconfig.json}, which decides what the compiler sees; empty if absent. */
  private String configurationHash() {
    try {
      return CachedTypeFacts.sha256(Files.readString(projectRoot.resolve("tsconfig.json")));
    } catch (IOException absent) {
      return "";
    }
  }

  @Override
  public void close() {
    if (running != null) {
      running.close();
    }
  }
}
