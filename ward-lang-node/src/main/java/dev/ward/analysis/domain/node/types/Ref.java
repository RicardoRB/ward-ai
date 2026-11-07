package dev.ward.analysis.domain.node.types;

import java.util.concurrent.atomic.AtomicReference;

/** A name bound to the scope it resolves in. The declarations are looked up on first use. */
public final class Ref {
  private final Lookup scope;
  private final String name;
  private final AtomicReference<Sym> resolved = new AtomicReference<>();
  private volatile boolean done;

  Ref(final Lookup scope, final String name) {
    this.scope = scope;
    this.name = name;
  }

  Ref(final Sym sym, final String name) {
    this.scope = null;
    this.name = name;
    this.resolved.set(sym);
    this.done = true;
  }

  /** Returns the declarations of the name, or null when the scope has none. */
  Sym sym() {
    if (!done) {
      resolved.set(Names.resolve(scope, name));
      done = true;
    }
    return resolved.get();
  }

  /** Returns the name as written. */
  String name() {
    return name;
  }
}
