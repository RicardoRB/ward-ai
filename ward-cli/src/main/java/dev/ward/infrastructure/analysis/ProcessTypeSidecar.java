package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.ExpressionTypes;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeResolver;
import dev.ward.analysis.domain.port.TypeSidecar;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A type sidecar run as a child process that speaks JSON lines on stdin and stdout, one request and
 * one response at a time. Any timeout, malformed line or exit marks it unavailable for the run; it
 * is never restarted. Its stderr is discarded, so no source text reaches the log.
 */
public final class ProcessTypeSidecar implements TypeSidecar {
  private static final Logger LOGGER = Logger.getLogger(ProcessTypeSidecar.class.getName());

  /** How long the sidecar may take to start and answer the probe request. */
  static final Duration START_TIMEOUT = Duration.ofSeconds(30);

  /** How long the sidecar may take to answer one file's request. */
  static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private static final Optional<String> END_OF_OUTPUT = Optional.empty();

  private final String language;
  private final List<String> command;
  private final Path workingDirectory;
  private Process process;
  private BlockingQueue<Optional<String>> responses;
  private BufferedWriter requests;
  private boolean usable;

  /**
   * Creates a sidecar that is started with {@code command} in {@code workingDirectory}.
   *
   * @param language the language name for diagnostics, such as {@code java}
   * @param command the argument vector, run without a shell
   * @param workingDirectory the project root the sidecar starts in
   */
  public ProcessTypeSidecar(
      final String language, final List<String> command, final Path workingDirectory) {
    this.language = language;
    this.command = List.copyOf(command);
    this.workingDirectory = workingDirectory;
  }

  @Override
  public Optional<TypeResolver> start() {
    if (!spawn()) {
      return Optional.empty();
    }
    final String probe = TypeSidecarJson.request("", "", List.of());
    if (exchange(probe, START_TIMEOUT, "start").isEmpty()) {
      return Optional.empty();
    }
    usable = true;
    return Optional.of(this::typesOf);
  }

  private boolean spawn() {
    try {
      process =
          new ProcessBuilder(command)
              .directory(workingDirectory.toFile())
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
    } catch (IOException startFailure) {
      unavailable("start-failure");
      return false;
    }
    requests =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    responses = new LinkedBlockingQueue<>();
    Thread.ofVirtual().start(() -> pump(process));
    return true;
  }

  /** Copies response lines to the queue until the output ends, then queues the end marker. */
  private void pump(final Process running) {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      while (line != null) {
        responses.add(Optional.of(line));
        line = reader.readLine();
      }
    } catch (IOException readFailure) {
      unavailable("read-failure");
    } finally {
      responses.add(END_OF_OUTPUT);
    }
  }

  /**
   * Sends one request line and waits up to {@code timeout} for its response. Empty when the answer
   * does not arrive, the process has exited, or the response is malformed.
   */
  private Optional<Map<String, String>> exchange(
      final String request, final Duration timeout, final String phase) {
    try {
      requests.write(request);
      requests.write('\n');
      requests.flush();
      final Optional<String> line = responses.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (line == null) {
        unavailable(phase + "-timeout");
      } else if (line.isEmpty()) {
        unavailable(phase + "-exit");
      } else {
        return Optional.of(TypeSidecarJson.types(line.get()));
      }
    } catch (IOException writeFailure) {
      unavailable(phase + "-write-failure");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      unavailable(phase + "-interrupted");
    } catch (IllegalArgumentException malformed) {
      unavailable(phase + "-malformed");
    }
    return Optional.empty();
  }

  private ExpressionTypes typesOf(final SourceUnit unit, final List<Span> spans) {
    if (!usable || spans.isEmpty()) {
      return ExpressionTypes.none();
    }
    final Map<String, Span> byKey = new HashMap<>();
    spans.forEach(span -> byKey.put(TypeSidecarJson.key(span), span));
    final String request = TypeSidecarJson.request(unit.path(), unit.text(), spans);
    final Optional<Map<String, String>> answer = exchange(request, REQUEST_TIMEOUT, "request");
    if (answer.isEmpty()) {
      usable = false;
      return ExpressionTypes.none();
    }
    final Map<Span, String> types = new HashMap<>();
    answer
        .get()
        .forEach(
            (key, type) -> {
              final Span span = byKey.get(key);
              if (span != null) {
                types.put(span, type);
              }
            });
    return new ExpressionTypes(types);
  }

  /** Stops the process; calling it again, or before a start, does nothing. */
  @Override
  public void close() {
    usable = false;
    if (process != null) {
      process.destroyForcibly();
    }
  }

  private void unavailable(final String category) {
    if (LOGGER.isLoggable(Level.WARNING)) {
      LOGGER.log(
          Level.WARNING,
          "event=type-sidecar-unavailable language=" + language + " category=" + category);
    }
  }
}
