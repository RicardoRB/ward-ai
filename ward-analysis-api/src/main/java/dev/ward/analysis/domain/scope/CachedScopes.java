package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remembers the scopes of each unit for the life of a run, by language, path and content hash, so
 * every rule and every gate that reads a unit's scopes shares one build.
 */
public final class CachedScopes implements ScopeAnalysis {
  private static final Logger LOGGER = Logger.getLogger(CachedScopes.class.getName());

  private final ScopeAnalysis delegate;
  private final Map<String, UnitScopes> built = new ConcurrentHashMap<>();

  /** Creates the cache over {@code delegate}. */
  public CachedScopes(final ScopeAnalysis delegate) {
    this.delegate = delegate;
  }

  @Override
  public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
    final String key =
        unit.language() + "|" + unit.path() + '|' + CachedTypeFacts.sha256(unit.text());
    final UnitScopes known = built.get(key);
    if (known != null) {
      if (LOGGER.isLoggable(Level.FINE)) {
        LOGGER.fine("event=scopes-cache-hit");
      }
      return known;
    }
    final UnitScopes fresh = delegate.analyze(unit, tree);
    built.put(key, fresh);
    return fresh;
  }

  @Override
  public Set<String> builtins(final Set<String> environments) {
    return delegate.builtins(environments);
  }
}
