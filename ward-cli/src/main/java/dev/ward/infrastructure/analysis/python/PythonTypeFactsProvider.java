package dev.ward.infrastructure.analysis.python;

import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.python.PythonStubs;
import dev.ward.analysis.domain.python.PythonTypeFacts;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Supplies the native Python type facts of a project to the engine. Nothing runs in another
 * process: stubs come from the project, the bundled standard-library stubs and the virtualenv.
 */
public final class PythonTypeFactsProvider implements TypeSidecar {

  private final TypeFacts facts;

  /** Creates the provider for the project at {@code projectRoot}, parsing with {@code parser}. */
  public PythonTypeFactsProvider(final Path projectRoot, final LanguageFrontend parser) {
    final PythonStubs stubs = new PythonStubs(parser, new PythonStubFiles(projectRoot)::text);
    this.facts = new CachedTypeFacts(new PythonTypeFacts(parser, stubs), "");
  }

  @Override
  public Optional<TypeFacts> start() {
    return Optional.of(facts);
  }

  @Override
  public void close() {
    // Nothing runs for Python, so there is nothing to stop.
  }
}
