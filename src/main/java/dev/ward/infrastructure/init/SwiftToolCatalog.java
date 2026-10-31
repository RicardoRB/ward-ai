package dev.ward.infrastructure.init;

/**
 * The SwiftLint release init pins in the Ward tools package. SwiftLintPlugins 0.65.1 is the newest
 * release at least 14 days old on 2026-10-31 (published 2026-08-21). Its {@code SwiftLintBinary}
 * artifact bundle is checksummed in the release's {@code Package.swift} as {@code
 * c3a1d77647ca18c1b7e9be7dbc6cd4490d26422f28814b76370244ff61970869}.
 */
final class SwiftToolCatalog {
  /** The SwiftLintPlugins repository the tools package depends on. */
  static final String SWIFTLINT_PLUGINS_URL = "https://github.com/SimplyDanny/SwiftLintPlugins";

  /** The exact SwiftLintPlugins version the tools package pins. */
  static final String SWIFTLINT_PLUGINS_VERSION = "0.65.1";

  private SwiftToolCatalog() {}
}
