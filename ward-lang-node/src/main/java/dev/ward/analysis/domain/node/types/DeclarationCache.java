package dev.ward.analysis.domain.node.types;

import java.util.Optional;

/** Keeps declaration indexes between runs. A cache that cannot read or write answers nothing. */
public interface DeclarationCache {

  /** A cache that keeps nothing. */
  DeclarationCache NONE =
      new DeclarationCache() {
        @Override
        public Optional<byte[]> load(final String key) {
          return Optional.empty();
        }

        @Override
        public void store(final String key, final byte[] bytes) {
          // nothing is kept
        }
      };

  /** Returns the bytes stored under {@code key}, or empty. */
  Optional<byte[]> load(String key);

  /** Stores {@code bytes} under {@code key}. */
  void store(String key, byte[] bytes);
}
