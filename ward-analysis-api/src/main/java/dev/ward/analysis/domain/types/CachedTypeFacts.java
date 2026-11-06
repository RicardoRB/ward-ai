package dev.ward.analysis.domain.types;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.port.TypeFacts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remembers the facts a sidecar answered for the life of a run, by the unit's content hash, the
 * hash of the classpath file, and the spans and kinds asked, so an unchanged unit is asked once.
 */
public final class CachedTypeFacts implements TypeFacts {
  private static final Logger LOGGER = Logger.getLogger(CachedTypeFacts.class.getName());

  private final TypeFacts delegate;
  private final String classpathHash;
  private final Map<String, UnitTypeFacts> answers = new ConcurrentHashMap<>();

  /**
   * Creates the cache over {@code delegate}.
   *
   * @param delegate the sidecar's facts
   * @param classpathHash the hash of the classpath file's content, or empty text when there is none
   */
  public CachedTypeFacts(final TypeFacts delegate, final String classpathHash) {
    this.delegate = delegate;
    this.classpathHash = classpathHash;
  }

  @Override
  public UnitTypeFacts factsOf(
      final SourceUnit unit, final List<Span> spans, final Set<FactKind> kinds) {
    final String key =
        unit.path() + '|' + sha256(unit.text()) + '|' + classpathHash + '|' + spans + '|' + kinds;
    final UnitTypeFacts known = answers.get(key);
    if (known != null) {
      if (LOGGER.isLoggable(Level.FINE)) {
        LOGGER.fine("event=type-facts-cache-hit");
      }
      return known;
    }
    final UnitTypeFacts fetched = delegate.factsOf(unit, spans, kinds);
    answers.put(key, fetched);
    return fetched;
  }

  /** Returns the SHA-256 of {@code text} in hex. */
  public static String sha256(final String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException("SHA-256 is always available", missing);
    }
  }
}
