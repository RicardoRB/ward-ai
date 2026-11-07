package dev.ward.analysis.domain.node.types;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** A name bound to the scope it resolves in. The declarations are looked up on first use. */
public final class NameRef {
  private final Lookup scope;
  private final String name;
  private final AtomicReference<Optional<Sym>> resolved = new AtomicReference<>();

  NameRef(final Lookup scope, final String name) {
    this.scope = scope;
    this.name = name;
  }

  NameRef(final Sym sym, final String name) {
    this.scope = null;
    this.name = name;
    this.resolved.set(Optional.ofNullable(sym));
  }

  /** Returns the declarations of the name, or null when the scope has none. */
  Sym sym() {
    Optional<Sym> known = resolved.get();
    if (known == null) {
      resolved.compareAndSet(null, Optional.ofNullable(Names.resolve(scope, name)));
      known = resolved.get();
    }
    return known.orElse(null);
  }

  /** Returns the name as written. */
  String name() {
    return name;
  }
}
