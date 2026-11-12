package dev.ward.application.license;

import dev.ward.domain.gate.ReportSink;
import java.nio.file.Path;
import javax.inject.Inject;

/**
 * Stores a generated document in the file named by {@code --output}. The sink replaces the file
 * only once the whole document is written, so a failed run keeps the earlier file.
 */
public final class DocumentOutput {
  private final ReportSink sink;

  /** Creates the output over the file port. */
  @Inject
  public DocumentOutput(final ReportSink sink) {
    this.sink = sink;
  }

  /** Writes {@code document} to {@code target}. */
  public void save(final Path target, final Document document) {
    sink.persist(target, document.content());
  }
}
