package dev.ward.infrastructure.gate;

import dev.ward.domain.gate.RevisionReadException;
import dev.ward.domain.gate.RevisionSnapshot;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * One commit read through one {@code git cat-file --batch} process, started on the first read. A
 * read writes one blob id and reads that one object back, so only one blob is held at a time; the
 * only data kept is the path to blob id listing.
 */
final class GitSnapshot implements RevisionSnapshot {
  private static final Logger LOG = Logger.getLogger(GitSnapshot.class.getName());
  private static final int META_FIELDS = 3;
  private static final String MODE_BLOB = "blob ";
  private static final int HEADER_FIELDS = 3;
  private static final int SIZE_FIELD = 2;

  private final Path root;
  private final Map<String, String> blobs;
  private final GitRevisionFiles.ProcessStarter starter;
  private Process process;
  private OutputStream requests;
  private InputStream answers;

  GitSnapshot(
      final Path root,
      final Map<String,
      String> blobs,
      final GitRevisionFiles.ProcessStarter starter
  ) {
    this.root = root;
    this.blobs = Map.copyOf(blobs);
    this.starter = starter;
  }

  /** Parses {@code ls-tree -r -z} output into path to blob id; submodules and others are left out. */
  static Map<String, String> blobs(final String listing) {
    return Arrays.stream(listing.split("\0"))
        .filter(entry -> entry.indexOf('\t') > 0)
        .filter(entry -> entry.substring(0, entry.indexOf('\t')).split(" ").length == META_FIELDS)
        .filter(entry -> entry.startsWith(MODE_BLOB, entry.indexOf(' ') + 1))
        .collect(
            Collectors.toMap(
                entry -> entry.substring(entry.indexOf('\t') + 1),
                entry -> entry.substring(
                    entry.lastIndexOf(' ', entry.indexOf('\t')) + 1,
                    entry.indexOf('\t')
                ),
                (first, second) -> second
            )
        );
  }

  @Override
  public List<String> files(final String directory) {
    final String prefix = directory.isEmpty() ? "" : directory + "/";
    return blobs.keySet().stream()
        .filter(name -> name.startsWith(prefix))
        .map(name -> name.substring(prefix.length()))
        .sorted()
        .toList();
  }

  @Override
  public Optional<String> read(final String path) {
    final String id = blobs.get(path);
    if (id == null) {
      return Optional.empty();
    }
    try {
      start();
      requests.write((id + "\n").getBytes(StandardCharsets.US_ASCII));
      requests.flush();
      return Optional.of(object(header()));
    } catch (IOException e) {
      LOG.log(Level.WARNING, "git cat-file --batch failed for a file");
      throw new RevisionReadException("Git could not read " + path, e);
    }
  }

  private void start() throws IOException {
    if (process == null) {
      process = starter.start(List.of("git", "cat-file", "--batch"), root);
      requests = process.getOutputStream();
      answers = new BufferedInputStream(process.getInputStream());
    }
  }

  private String header() throws IOException {
    final StringBuilder line = new StringBuilder();
    int next = answers.read();
    while (next != '\n') {
      if (next < 0) {
        throw new IOException("git closed its output");
      }
      line.append((char)next);
      next = answers.read();
    }
    return line.toString();
  }

  private String object(final String header) throws IOException {
    final String[] fields = header.split(" ");
    if (fields.length != HEADER_FIELDS) {
      throw new IOException("git has no such object");
    }
    final byte[] content = answers.readNBytes(Integer.parseInt(fields[SIZE_FIELD]));
    answers.read(); // the newline after the object
    return new String(content, StandardCharsets.UTF_8);
  }

  @Override
  public void close() {
    if (process != null) {
      process.destroyForcibly();
    }
  }
}
