package dev.ward.infrastructure.analysis.python;

import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.domain.python.PythonStubs;
import dev.ward.analysis.domain.python.PythonTypeFacts;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import java.util.Optional;

/**
 * The Python type facts, answered in process from annotations, stubs and inference. Nothing is
 * started, so there is nothing to close and the facts are always available.
 */
public final class PythonTypeSidecar implements TypeSidecar {

  private final LanguageFrontend parser;
  private final PythonStubFiles files;

  /** Creates the sidecar that parses with {@code parser} and reads stubs through {@code files}. */
  public PythonTypeSidecar(final LanguageFrontend parser, final PythonStubFiles files) {
    this.parser = parser;
    this.files = files;
  }

  @Override
  public Optional<TypeFacts> start() {
    final PythonStubs stubs = new PythonStubs(parser, files::text);
    return Optional.of(new CachedTypeFacts(new PythonTypeFacts(parser, stubs), ""));
  }

  @Override
  public void close() {
    // No process is started, so there is nothing to stop.
  }
}
