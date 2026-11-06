package dev.ward.infrastructure.format;

import dev.ward.domain.config.WardException;
import dev.ward.domain.format.SourceReader;
import dev.ward.domain.format.SourceSink;
import dev.ward.domain.format.SourceText;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Reads and writes source files on the local file system. A write goes through a temporary file in
 * the same folder, which then replaces the original, and keeps the original's permissions.
 */
public final class FileSystemSourceFiles implements SourceReader, SourceSink {
  private static final Logger LOGGER = Logger.getLogger(FileSystemSourceFiles.class.getName());
  private static final byte[] BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
  private static final String TEMP_PREFIX = ".ward-format-";
  private static final String TEMP_SUFFIX = ".tmp";

  /** Creates the adapter. */
  @Inject
  public FileSystemSourceFiles() {}

  @Override
  public SourceText read(final Path file) {
    try {
      final byte[] bytes = Files.readAllBytes(file);
      final boolean bom = startsWithMark(bytes);
      final int offset = bom ? BYTE_ORDER_MARK.length : 0;
      final String text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
              .toString();
      return new SourceText(text, bom, Files.isSymbolicLink(file));
    } catch (final CharacterCodingException exception) {
      throw new WardException("Could not read " + file + " as UTF-8.", exception);
    } catch (final IOException exception) {
      throw new WardException("Could not read " + file + ".", exception);
    }
  }

  @Override
  public void persist(final Path file, final String text, final boolean byteOrderMark) {
    if (Files.isSymbolicLink(file)) {
      throw new WardException(file + ": symbolic link, not rewritten");
    }
    final Path temporary = temporaryBeside(file);
    try {
      Files.write(temporary, content(text, byteOrderMark));
      copyPermissions(file, temporary);
      replace(temporary, file);
    } catch (final IOException exception) {
      discard(temporary);
      throw new WardException("Could not write " + file + ".", exception);
    }
  }

  private static Path temporaryBeside(final Path file) {
    try {
      return Files.createTempFile(parentOf(file), TEMP_PREFIX, TEMP_SUFFIX);
    } catch (final IOException exception) {
      throw new WardException("Could not create a temporary file beside " + file + ".", exception);
    }
  }

  private static Path parentOf(final Path file) {
    return Objects.requireNonNull(file.toAbsolutePath().getParent(), "parent");
  }

  private static byte[] content(final String text, final boolean byteOrderMark) {
    final byte[] body = text.getBytes(StandardCharsets.UTF_8);
    if (!byteOrderMark) {
      return body;
    }
    final byte[] bytes = new byte[BYTE_ORDER_MARK.length + body.length];
    System.arraycopy(BYTE_ORDER_MARK, 0, bytes, 0, BYTE_ORDER_MARK.length);
    System.arraycopy(body, 0, bytes, BYTE_ORDER_MARK.length, body.length);
    return bytes;
  }

  private static void copyPermissions(final Path file, final Path temporary) throws IOException {
    try {
      final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
      Files.setPosixFilePermissions(temporary, permissions);
    } catch (final UnsupportedOperationException exception) {
      LOGGER.fine("event=format-permissions skipped: file system has no POSIX permissions");
    }
  }

  private static void replace(final Path temporary, final Path file) throws IOException {
    try {
      Files.move(
          temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (final AtomicMoveNotSupportedException exception) {
      LOGGER.fine("event=format-move non-atomic: file system does not support atomic moves");
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void discard(final Path temporary) {
    try {
      Files.deleteIfExists(temporary);
    } catch (final IOException exception) {
      LOGGER.fine("event=format-cleanup left a temporary file behind");
    }
  }

  private static boolean startsWithMark(final byte[] bytes) {
    if (bytes.length < BYTE_ORDER_MARK.length) {
      return false;
    }
    for (int i = 0; i < BYTE_ORDER_MARK.length; i++) {
      if (bytes[i] != BYTE_ORDER_MARK[i]) {
        return false;
      }
    }
    return true;
  }
}
