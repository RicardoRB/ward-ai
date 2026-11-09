package dev.ward.analysis.domain.node;

import java.util.Set;

/** The Node built-in module names that an import may name without a package. */
final class NodeBuiltins {

  static final Set<String> NAMES =
      Set.of(
          "assert",
          "async_hooks",
          "buffer",
          "child_process",
          "cluster",
          "console",
          "constants",
          "crypto",
          "dgram",
          "diagnostics_channel",
          "dns",
          "domain",
          "events",
          "fs",
          "http",
          "http2",
          "https",
          "inspector",
          "module",
          "net",
          "os",
          "path",
          "perf_hooks",
          "process",
          "punycode",
          "querystring",
          "readline",
          "repl",
          "stream",
          "string_decoder",
          "sys",
          "timers",
          "tls",
          "trace_events",
          "tty",
          "url",
          "util",
          "v8",
          "vm",
          "wasi",
          "worker_threads",
          "zlib",
          "test");

  private NodeBuiltins() {}
}
