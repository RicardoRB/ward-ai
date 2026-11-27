# Changelog

All notable changes to Ward are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and Ward uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Ward is in alpha, so minor versions may
still change behavior. Breaking changes are called out under **Changed**.

## [Unreleased]

### Fixed

- The Node taint rules treat the rows that an ORM reads (Sequelize `findByPk`, `findOne`, `findAll`; TypeORM `findOneBy`, `findBy` and the other repository reads; Prisma `findUnique`, `findFirst`, `findMany`) as stored data that users wrote, with `possible` confidence, so default `strict` stays quiet and `min-confidence = "possible"` shows them. A taint specification source may now carry `assumed = "<reason>"` to make its flows `possible`. A method of an exported object (`security.authenticatedUsers.get(token)`) is named `module#object#method` and `ns.object.method()` is followed across a relative import, so a keyed read does not carry the key's taint: the Juice Shop false positives `profileImageUrlUpload.ts:29` and `profileImageFileUpload.ts:42` are gone (#748), and the five `routes/userProfile.ts` true positives stay, through the stored user (#786). `file-type`'s `fromBuffer` is a path sanitizer. The Juice Shop benchmark now runs with `min-confidence = "possible"`.
- `ward:typescript/path-traversal` and `ward:javascript/path-traversal` treat a key as a name of the program past a null check of the lookup that vouched for it (#748): a function that returns `null` unless `if (known.has(key))` holds, where `known` comes from `await getter()`, vouches for the `key` of a caller that exits on `x == null` or `!x` after `x = await f(key)`. The collection must be one that only its file writes to (a top-level `let` or `const` that starts as `null` or an empty `new Map()` or `new Set()`, and is only read, compared with `null`, reset, written with `set`, or returned by the getter), every call of the getter in the project and its dependents must keep the result in a local that is only read or written with `set`, and no key of such a `set` may carry taint. A map written with request data, a check whose null branch does not exit, a lookup with another return, and a collection handed to another function still report. `vulnCodeSnippet.ts:89` and `:90` of Juice Shop leave the list; `vulnCodeFixes.ts:83` stays.
- `ward:typescript/dom-xss`, `ward:javascript/dom-xss` and `unsafe-framework-trust` no longer report a value that only holds data the program wrote (#749, #788). A parameter of a function that is not exported counts as such data when the file calls it only by name and every call passes it: a string constant, an element of a constant array or object of constants, in the file or in a file it imports, or the output of `snarkdown(...)` over such data (the default export of the `snarkdown` package turns a Markdown string into an HTML string and reads nothing else). An array or object counts only when no code of the two files can change it: assigning to a member, calling a method that is not an element accessor, or sending it where the analysis does not follow it keeps the finding, as do an exported function, a function passed as a value, and one call that passes request data. These four rules now read the files their file imports, so a cached result of a Node taint rule is keyed on the text of those files instead of on a taint summary. Known limit: a file that imports an exported constant and is not among the files the sink's file depends on can still change it unseen.
- `ward:typescript/path-traversal` and `ward:javascript/path-traversal` treat a key as a name without a path separator past a check of a cached listing (#797): a function with one parameter `key` that returns only `cache[key]`, where `cache` is a top-level empty object that only that function mentions and writes `cache[key] = { list }`, with `list` a local array that only `list.push(...)` inside `if (entry.startsWith(`${key}...`))` fills, in `for (const entry of entries)` over `const entries = readdirSync(...)`. A caller that exits on `x.list.length === 0` (or `!x.list.length`) or reads `x` in the `else` branch of it vouches for its `key`. A cache that another place writes or exposes, a list that grows outside the prefix test, a listing without the prefix test, and a caller whose empty branch does not exit still report. `vulnCodeFixes.ts:83` of Juice Shop leaves the report.

## [0.12.1] - 2026-11-25

The `strict` preset reports fewer false positives in every language (#726): each one found by the pinned corpora is now a passing fixture or is listed with an issue, taint findings carry a confidence, and the differential corpus covers Python, TypeScript/JavaScript and Swift.

### Added

- The differential corpus covers Python, TypeScript/JavaScript and Swift (#760): the full-stack FastAPI template, NestJS and Vapor are pinned in `corpus/archives.txt`, measured with the `strict` preset by three new corpus smoke tests, and `diff-findings.sh` and the `corpus-diff` job accept `python`, `node` and `swift`. The three false-positive causes and ten parser gaps it found are open as issues and listed in the corpus files.

### Changed

- **Taint findings have a confidence, and every taint rule reports only `proven` flows by default** (#762). A flow is `possible` when it crosses a step the analysis assumed: a call into project code that it does not follow (an overload, a call through an interface with several implementors, a receiver whose type is not known) or a field that one method writes and another reads. A proven flow to the same place always wins, so no fully resolved flow is hidden, and a function whose own flow is `possible` passes that on to its callers. The rule option `min-confidence = "possible"` reports the `possible` flows too. `ward check --format json` writes `confidence` and, for a possible flow, `assumptions` on each taint finding, and SARIF writes them in the result `properties`. Over the pinned corpora (Java, Kotlin, Healthchecks, CTFd, got, Juice Shop, Swift) no finding turns `possible`, so no labelled true positive is hidden; the C# holder object passed to `Process.Start` stays proven, as a container that a sink receives does hold the value.

### Fixed

- `ward:swift/duplicated-key-in-dictionary-literal` no longer reports a literal that initializes a binding annotated with a named type other than `Dictionary`, such as `let headers: HTTPHeaders = [...]`, which keeps repeated keys (#784, in part: a literal passed as an argument is still reported).
- `ward:python/hashlib-insecure-hash-function` follows where an MD5 or SHA-1 digest goes (#732), instead of reporting every call. A digest that only names a file or a path (`os.path.join`, `open`, the stem of a file name) or keys a mapping is not reported. A digest that is compared, given to `hmac` or `compare_digest`, returned from a function that no call of the file takes, or used for nothing is reported as before. A digest that goes where the analysis does not follow it (stored in a field or an item, passed to another call, returned to callers that only store it, as `hash_file` of CTFd) is a `possible` finding, shown with `min-confidence = "possible"`; the callers in other files are not seen. A call with `usedforsecurity=False` is still skipped.
- The taint rules for TypeScript and JavaScript follow a call through an import of another file: a function that a file exports is named `module#name`, and `import { f } from '../lib/files'`, `import * as files from '../lib/files'` and `require` reach it, so the summary of the callee applies. A callee that returns a constant is no longer a path, and the request query that reaches `logger.warn` inside `findChallengeByName` of Juice Shop is a new true positive (`ward:typescript/log-forging`). The rules of the `ward-lang-node` taint family have the `TAINT` capability, so their findings follow the files they depend on.
- A function that returns an operand of a conditional or logical operator (`return str.length > n ? str.substr(0, n) : str`) returns the taint of that operand: the callee summary lost it, so such a helper looked clean once its call was resolved (`utils.trunc` of Juice Shop).
- `ward:typescript/path-traversal` and `ward:javascript/path-traversal` read `obj[key]`, and `map.get(key)` on a `Map` that the function declared, as a member of the object: the key adds nothing to the value. A parameter, and a tainted object or map, still report. The `profileImage*` and `vulnCode*` lines of Juice Shop stay listed (#748), as `hacking-instructor/index.ts:126` does (#749): they need an object-literal method (`authenticatedUsers.get`), a proof that a function answers 404 unless a key is a member of a set it reads from disk, or an exported constant table of another file.
- `ward:python/code-injection` no longer reports a method named `exec` or `eval` (SQLModel and SQLAlchemy `session.exec(statement)`): only the builtins, written bare or as `builtins.exec`, are sinks (#781).
- `ward:typescript/prototype-pollution` and `ward:javascript/prototype-pollution` no longer report a string, template or regular-expression literal that begins with `__proto__=`; property writes still report (#782).
- `ward:swift/weak-random` no longer reports the declaration of a function named `random` or `rand`, nor a call of a function that the same file declares; a call of the C library generator still reports (#783).
- `ward:typescript/xss` and `ward:javascript/xss` no longer report a value written to a response that is typed `text/plain` and sends `X-Content-Type-Options: nosniff`, set in the body or by a helper that sets both on the response it is given; HTML, a type that is not a literal, a type changed or a header removed afterwards, a response without `nosniff`, and headers set on another response still report (#785).
- The TypeScript and JavaScript parser accepts the constructs that left 82 files of the NestJS differential corpus unanalysed: a trailing comma in a type parameter list, type predicates (`x is T`, `asserts x is T`, `this is T`, `module is T`) in class and interface member signatures, `import('module')` types including `typeof import('module')`, type arguments on decorators, an optional generic method (`name?<T>()`), several decorators on one constructor parameter, and decorators before `export class` in JavaScript (#771, #772, #773, #774, #775).
- The Swift parser reads `mutating` and `nonmutating` accessors and `} set {` on one line, `async` as a name, enum cases with labelled associated values (`case host(_ host: String?, port: Int?)`), raw strings that hold quotes, `\u{…}` escapes of any length, `[Key: Any]()` calls, `[...]` subscripts, `value?[...]` and `;` between members, so the 25 Vapor files it rejected are analysed (#776, #777, #778, #779, #780); `implicit-getter` no longer reports a `get` next to a `nonmutating set`.
- The Kotlin taint rules follow a request value through a layered service: a call through a constructor property (`class Controller(private val service: Service)`) reaches the method of `Service`; `import org.springframework.web.bind.annotation.*` finds `@RequestParam`, `@PathVariable` and `@RequestBody`; a `val` that holds `LoggerFactory.getLogger(...)`, `Logger.getLogger(...)`, `LogManager.getLogger(...)` or `LogFactory.getLog(...)` has the logger type, so `ward:kotlin/log-forging` reports it; an extension function of the same file is followed with its receiver as the first argument and `this`; `toInt()`, `toLongOrNull()` and the other string conversions clean the value (#760).
- `ward:kotlin/untrusted-redirect` reports `call.respondRedirect(url)` of Ktor and `ward:kotlin/server-side-request-forgery` the `get`, `post`, `request` and related calls of a Ktor `HttpClient` (#760).
- The Kotlin taint rules treat a value as clean past `if (x !in ALLOWED) throw ...`, `if (!ALLOWED.contains(x)) return`, `require(x in ALLOWED)` and `check(ALLOWED.contains(x))` when `ALLOWED` is a `val` of the file that holds `setOf` or `listOf`, also when the check is made in a method that the caller calls, as for Java (#759).
- The Kotlin parser accepts the range-until operator `..<` and a bound callable reference on a class literal (`value::class::simpleName`); the two files of ktor-samples that it rejected are analysed (#767; the underscore float literal named in the issue was not the cause).
- `ward:typescript/path-traversal` and `ward:javascript/path-traversal` treat a name as clean inside `if (LIST.includes(x))` and past `if (!LIST.includes(x))` when that branch exits, in `const v = LIST.includes(x) ? x : 'default'` and, for a function that throws unless its parameter is on the list, after a call to it (`LIST` is a literal array or a constant table of the file; `.has` works too). A list built at run time, a ternary that gives another value, a guard that does not exit and a function that only returns early still report (#748; the `profileImage*`, `vulnCode*` lines stay listed: they need the lookup in another function or file).
- The taint analysis of TypeScript and JavaScript follows a value that a conditional or logical operator picks (`const v = req.query.a || 'x'`, `c ? a : b`, `x = a ?? x`): it was lost before, so these flows were not reported; a condition alone does not taint the value. Juice Shop shows seven more true positives (`search`, `trackOrder`, `showProductReviews` and four wrong coding-challenge fixes).
- A function whose parameter check leaves it clean now vouches for the argument of its callers in TypeScript and JavaScript too: the call span of those calls is only the name, and the clean write used to land before the call.
- `ward:typescript/xss` and `ward:javascript/xss` no longer report `res.end(JSON.stringify(x))` or `res.write(...)` when the same response was given a JSON content type before the write on every path of the handler (`setHeader`/`header`/`set`/`append` of `Content-Type`, `type`, `writeHead`), and only when the `JSON.stringify` call is the whole argument; without a content type, with `text/html`, with a type set on another response or only under a condition, the write is still reported (#757). A taint sink may list `except-calls` for this.
- `ward:typescript/path-traversal` and `ward:javascript/path-traversal` treat a path that was resolved with `path.resolve`, `normalize`, `join` or `realpath` as clean inside `if (p.startsWith(base))` and past an `if` that exits when `!p.startsWith(base)` holds, and a value read from a table of constants (`TABLE[key]`, `TABLE.get(key)`) as the program's own (#748; only the `server.ts` guard is fixed, the other five lines stay listed).
- `ward:typescript/dom-xss`, `unsafe-framework-trust` and `cleartext-transport` (and the JavaScript rules) now read the value they report through the data flow of the function: a value built from constants, arithmetic, comparisons, `Number(...)`, `.toFixed(...)` and `.length` holds no markup, and a request whose host is the `address` that a server reports for itself stays on the machine (#750, #758).
- The taint analysis follows an arrow function whose body is one expression (`const pageOf = (url) => Number(url) || 1`) like a function with a `return`, so a sanitizer inside it is seen at the call (#742).
- `ward:swift/final-test-case` no longer reports a test case class that another class of the project inherits from, since a base class cannot be final (#755).
- `ward:python/path-injection` and `ward:python/template-injection` treat a name as clean inside `if name in names:` and past `if name not in names:` when that branch exits, only when `names` is a literal of constants, a name bound once to one, or the listing of a directory (#734; the `send_file(output)` lines stay listed).
- `ward:python/unused-function`, `unused-class`, `unused-method` and `unused-property` count the words of `*.html`, `*.jinja`, `*.jinja2`, `*.j2` and `*.txt` files as used names, so members that only a template reads are not reported (#728, #729).
- The Python unused-definition rules count `upgrade`/`downgrade` (alembic), the `handle_*` callbacks of `HTMLParser` and `_send`/`pipeline`/`close` of `StatsClientBase` as used (#729).
- `ward:java/log-forging` no longer reports `getRequestURI()`, the path as sent, which a servlet container does not accept with a raw line break; `getQueryString` and `getPathInfo` still report, also through `URLDecoder.decode`; `URLDecoder.decode` and `UriUtils.decode` of a direct `getRequestURI()` call report, since decoding can produce a line break; the decode of a constant or of a stripped value does not (#763, #770).
- The Java taint rules take the getters of a type that a `@RequestBody` or `@ModelAttribute` parameter binds as sources wherever the project reads them, also in another file and on an entity loaded again from storage (the generator-path shape of #769). That step is assumed, so such a flow is `possible` and shows only with `min-confidence = "possible"`; the entity that is the handler parameter itself stays `proven`. A call entry whose name ends with `*` matches a zero-argument call by prefix.
- `ward:java/header-splitting` no longer reports a header written from the `length` of an array or the `size()` of a collection; the array itself written to a header still reports (#764).
- `ward:java/path-injection`, `ward:java/regex-injection` and the other taint rules take `System.getProperty` of `user.dir`, `user.home`, `java.io.tmpdir`, `line.separator`, `file.separator`, `path.separator` or `os.name` with a constant key as trusted; a computed key or a custom property is still a source (#765).
- `ward:java/csrf-protection-disabled` skips a statement whose chain sets `sessionCreationPolicy(STATELESS)` and has no `formLogin`, `httpBasic` or `rememberMe` (#766).
- `ward:rust/get-first` reports only a receiver whose type is known to be a slice, array, `Vec` or `VecDeque`: a local is typed from the return type of a function or method of the file (through `?`, `unwrap` and `expect`, and `Self::Assoc` when an impl of the file fixes it) and from `for` and iterator-closure items of a known collection; a receiver of unknown type, such as a `Captures` returned by a trait method, is no longer reported, and the file-defines-`get` fallback is gone (#761).
- `ward:python/untrusted-redirect` no longer reports a request value that only follows a literal `?` in a concatenation or f-string, nor a call to a function of the file that returns only such a value; a value before the `?` is still reported. A new `query-part` sanitizer kind of the taint specification names those value parts (#733).
- `ward:python/path-injection` no longer reports a name assigned a member of a table that the program fixes with `TABLE.get(key)` or `TABLE[key]`, whatever the key is (a table of constants, or of the functions the file defines), and treats `io.BytesIO(...)` and `io.StringIO(...)` as buffers, not paths (#734).
- The Python taint rules follow a call into another module of the project: a function body is named by its module (`pkg.mod#name`), and a call of `from pkg.mod import f` or `m.f()` after `import pkg.mod as m` reaches it, so a value that `def dump_csv(name): return StringIO()` gives back is a buffer at the caller and no longer a path for `ward:python/path-injection` (#734). A function with `*args` or `**kwargs` is not followed, since its arguments cannot be matched by position.
- `ward:python/untrusted-redirect` no longer reports a value that a condition checked with a function of the project, in `if f(x):`, `if a and f(x):`, and past `if not f(x): <exit>` or `if a or not f(x): <exit>`, when the body of `f`, in any module, proves that the host of a value made from the argument is the host of the request or that it has no host: `if parsed.netloc: return False`, a `netloc` or `hostname` compared with `==` or `in` in the returned expression, or the result of another such function. A guard that does not exit, a function that can return true without the check, a value that the region assigns again and another value than the checked one still report (#733).
- `ward:python/sql-injection` treats an operand of a comparison as not being part of the SQL text, so `table.select().where(table.c.route == route)` is bound and not reported, which the calls of a function of another module would otherwise expose (#734).
- `ward:swift/async-without-await` no longer reports a function that witnesses an async requirement of a protocol declared in the project; it also skips one that witnesses an async requirement of an Apple SDK protocol its type conforms to (`URLSession*Delegate`, `AppIntent.perform()`, entity queries, `UNUserNotificationCenterDelegate`, WebKit delegates and others, listed in `async-requirements.txt`); the same method on a type that does not conform is still reported (#756).
- `ward:java/openjdk-annotation-location` accepts the layout `ward format` writes for a parameter or record component whose annotation spans lines, `) @B String x` (#700).
- `ward:java/left-curly` no longer reports the `{` of an annotation array that `ward format` moves to its own line (#705).
- `strict` no longer enables `ward:java/avoid-no-argument-super-constructor-call`, which contradicted `ward:java/call-super-in-constructor`; the pair is guarded by `PresetContractTest` (#701).
- A `[select]` that holds both rules of a contradicting pair (for example `java.array-trailing-comma` and `java.no-array-trailing-comma`) is now rejected with an error that names the pair, instead of reporting findings no code can fix (#711).
- C# text rules no longer read the code of an inactive `#if`, `#elif` or `#else` branch: it is blanked like a comment, so a match there is not reported (#708). No symbol is defined at the start of a file, as with `csc` without `/define`; the project's own symbols are not read.
- `ward init` no longer writes the `controller` / `repository` layer rule, or the matching starter rule of `ward-architecture.toml`, for Rust and C# projects, where it could never fire (#710).
- `ward:rust/len-zero` no longer reports `self.len() == 0` inside an `is_empty` method (#709).
- `ward:rust/wildcard-imports` leaves the glob of the variants of an enum, such as `use self::Kind::*`, to `enum-glob-use` (#709).
- `ward:rust/get-first` skips a file that defines its own `get` method, whose `get(0)` is not a slice access (#709).
- `ward:rust/non-camel-case-types` reads the name of an enum variant, not the attribute above it, and honours `#[allow(non_camel_case_types)]`; `non-snake-case` and `non-upper-case-globals` honour their `allow` too (#709).
- `ward:rust/non-snake-case` accepts raw identifiers such as `r#async` and constants used as match patterns (#709).
- `ward:rust/non-upper-case-globals` skips `const _` (#709).
- `ward:rust/single-component-path-imports` skips aliased imports such as `use serde_json as json;` and `pub use` re-exports (#709).
- `ward:rust/unused-import-braces` skips a braced import with a rename, `use a::{self as b};` (#709).
- `ward:rust/redundant-semicolons` skips the `;` after a statement that is a macro call with braces (#709).
- `ward:rust/needless-return` reports a `return` only when it is the last statement of the function (#709).
- `ward:rust/unreachable-code` knows that a `break` in the tokens of a macro such as `select!` ends a `loop` (#709).
- `ward:rust/unused-assignments` no longer reports a variable that a closure or async block names, or a name that starts with `_` (#709).
- `ward:rust/get-first` decides per call from the receiver's declared type: it reports `.get(0)` on a slice, array, `Vec` or `VecDeque` even in a file that defines its own `get`, and skips only a receiver of another type (#768).
- `ward:rust/unused-assignments` gives a closure or async block its own flow graph: a dead store of a variable the block declares is reported again, and only a variable the block reads from outside is a capture, not one it only writes or shadows (#768).
- `ward:rust/unreachable-code` honours `#[allow(unreachable_code)]` on the statement after an endless `loop`, which the own graph of a closure or async block now exposes (#768).
- `ward:rust/unused-variables` reads the names in the format string of any macro, raw strings and `macro_rules!` bodies, skips const generic parameters, and skips a constant or `let` under `#[cfg(..)]`, whose alternative may be the one used (#709).
- `ward:rust/unsafe-public-function` no longer reports `pub(crate)` functions, which are not public (#709).
- `ward:swift/unused-local-variable` no longer reports a name declared in each branch of an `#if` block and read after it (#752).
- `ward:swift/unused-parameter` counts the read of a parameter by `guard case .x(let name) = name` that binds the same name (#753).
- `ward:swift/weak-random` accepts `drand48` and `lrand48` when the same file seeds them with `srand48` and a constant or a seed parameter; a seed from the clock still reports (#754).
- `ward:java/duplicate-code` leaves `package` and `import` declarations out of the comparison, so two files that share only their imports are no longer reported as copies (#706).
- `ward:java/log-forging` takes the `replace` / `replaceAll` chain that removes `\r` and `\n` as a sanitizer on a receiver that is a call to `String.valueOf`, `String.format`, `String.join`, `Objects.toString` or `toString()`, and through a method of the project that returns the stripped value. A method that keeps one of the line breaks is still reported (#712).
- `ward:java/server-side-request-forgery` no longer reports a value that only fills the query or the fragment of a URL whose `http` or `https` scheme, host and `?` or `#` are in a constant prefix (`"https://host/p?x=" + v`, `String.format`, `formatted`). A value before the `?` or in the host is still reported (#759).
- `ward:java/server-side-request-forgery` follows an allow-list check into a method of the project: an argument that a method checks with `if (!ALLOWED.contains(v)) throw ...` is clean in the caller after the call, and a method that checks its parameter and returns an address built from it gives a clean value. A method whose check returns instead of throwing, or that can return before the check, does not count (#759).
- Taint analysis in every language treats a call to a method of the project as a clean value when no parameter reaches a value it returns and it passes none to a sink, so a helper that strips or checks its input is a sanitizer for its callers.

## [0.12.0] - 2026-11-22

Ward now checks Rust and C# projects (#642).

### Added

- `ward init` detects Cargo projects (#647) and .NET projects (#657), and sets up their gates, and finds `cargo`, `cargo-llvm-cov`, `cargo-mutants` and `dotnet` on the `PATH`.
- A Rust frontend (#648): a Rust grammar for editions 2021 and 2024 (`let`-`else`, let chains, async closures, C strings, raw borrows, `unsafe extern`), scope analysis that follows shadowing, imports, `if let` and match arms, and one control-flow graph per function with a body.
- A C# frontend (#658) written from the C# language specification, with preprocessor handling, scopes and control flow.
- `ward format` formats Rust (#649) and C#, matching the whitespace of `dotnet format` (#659).
- Rust rules mapped to Clippy and the rustc lints (#650): 92 `ward:rust/*` rules in the `standard` and `strict` presets. Every lint of Clippy 0.1.88 and rustc 1.88 is classified in the catalog as implemented, planned (#681–#683) or out of scope with a reason. `ClippyParitySmokeTest` compares the fixtures with the real Clippy.
- C# rules mapped to the .NET analyzers (CA), the Roslyn code-style rules (IDE) and StyleCop (#660), with planned rows in #677–#680, and a duplicate-code rule for C#.
- Dependency inventory, OSV vulnerabilities, licenses and SBOM for Cargo (#651) and NuGet (#661) projects, and the `build-policy` gate for `Cargo.toml` (#652) and NuGet package declarations (#662).
- Tests, coverage, mutation and architecture gates for Rust (`cargo llvm-cov`, `cargo-mutants`; #653) and C# (Cobertura, Stryker.NET; #663).
- The `api-compat` gate compares the public API of Rust crates (#654) and C# projects (#664).
- Taint analysis and security rules for Rust (command, SQL and path injection, disabled TLS verification, `unsafe` in public functions; #655) and seven `ward:csharp/*` security rules (#665).
- Rust and C# corpus smoke tests and compatibility matrices, with CI jobs `rust-matrix` and `csharp-matrix` (#656, #666).
- The Java corpus now measures the `strict` preset as well as the security rules, and adds Spring Data Examples as a modern corpus. A finding labelled as a false positive fails the test unless `java-corpus-approximations.txt` lists it with an open issue. `PresetContractTest` rejects contradictory or Android-only rules in a preset, and `UnknownTypeRulesTest` checks that a type rule reports nothing when the type is unknown (#698).

### Fixed

- `ward:java/header-splitting`, `ward:java/log-forging` and `ward:java/cross-site-scripting` treat a call on a `String` that returns a number or a boolean (`length`, `isEmpty`, `indexOf`, `equals`, ...) and `Math.max`, `Math.min` and `Math.abs` as sanitizers, since a number cannot carry a line break or markup (#704).
- `ward:java/cross-site-scripting` no longer reports a response body written with `getWriter()` or `getOutputStream()` in a method whose content types are all JSON; an HTML or non-literal content type is still reported (#704).
- The Java taint rules, such as `ward:java/server-side-request-forgery`, treat a variable as clean past `if (!ALLOWED.contains(value)) { ...; throw or return }` when `ALLOWED` is a `final` collection field with an initializer (#704).
- Fields that Lombok writes or reads, and classes it gives a constructor, are no longer reported by `field-no-init`, `np-unwritten-field`, `uwf-unwritten-field`, `uuf-unused-field` and `missing-static-method-in-non-instantiatable-class` (#713).
- `ward:java/replace-java-util-vector` reports `Vector` only when it is not imported from another package (#714).
- `ward:java/avoid-no-argument-super-constructor-call` skips a qualified `outer.super()` call (#715).
- `ward:java/unused-assignment` counts a read in a `finally` block as a read of the value assigned before the `try` (#716).
- `ward:java/dmi-collections-should-not-contain-themselves` reports only a receiver known to be a collection (#717).
- `ward:java/eq-other-no-object` skips a typed `equals` overload in a class that also declares `equals(Object)` (#718).
- `ward:java/eq-overriding-equals-not-symmetric` no longer judges a class whose nested superclass in the same file may define `equals` (#719).
- `ward:java/sing-singleton-has-nonprivate-constructor` reports only when the getter returns the shared instance (#720).
- `ward:java/unused-local-variable` no longer gives the reads of a local to a method with the same name (#721).
- `ward:java/uw-uncond-wait` no longer reports a static helper named `wait` (#722).
- `ward:java/mockito-usage` reports only calls on `org.mockito.Mockito`.
- The message of `ward:java/inner-type-last` names the inner type.

## [0.11.4] - 2026-11-20

### Fixed

- `ward:java/rv-return-value-ignored` and `ward:java/return-value-ignored` no longer report a call that is the value of a `case ... ->` rule in a `switch` used as an expression; an arrow rule of a `switch` statement is still reported (#702).
- `ward:java/log-forging` treats a chain of `String.replace` or `replaceAll` calls with literal arguments that removes both carriage returns and line feeds as a sanitizer of its receiver (#703).
- `ward:java/unsafe-deserialization-settings` reports `enableDefaultTyping` only with no argument, a `DefaultTyping` constant or `LaissezFaireSubTypeValidator`, not with a validator that lists the allowed types (#704).
- The message of `ward:java/csrf-protection-disabled` names `exclude-paths` in `ward-rules.toml` as the way to leave out an API that no browser calls with cookies, since Ward has no suppression comment (#704).

## [0.11.3] - 2026-11-20

### Fixed

- `ward:java/multiple-variable-declarations` no longer counts the pattern variables of a `switch` or record pattern as declarations (#690).
- `ward format` breaks an `extends`, `implements` or `permits` list after a comma, one continuation deeper, when the joined list does not fit the line width (#694).
- The `strict` preset no longer enables `ward:java/interface-member-implied-modifier` and `ward:java/class-member-implied-modifier`, which contradicted `ward:java/redundant-modifier` (#685). They stay in the catalog.
- The `strict` preset no longer enables `ward:java/no-array-trailing-comma`, which contradicted `ward:java/array-trailing-comma` and the layout of `ward format` (#695).
- `ward:java/static-or-default-interface-method`, an Android-only rule, no longer runs in `strict` on plain JVM projects (#686).
- `ward:java/array-is-stored-directly` no longer reports a parameter whose type only has an array as a type argument, such as `Provider<byte[]>` (#687).
- `ward:java/mf-class-masks-field` no longer reports `serialVersionUID` in a subclass of a JDK class such as `RuntimeException`: a package-private member is inherited only inside its own package (#688).
- `ward:java/loose-coupling` no longer reports the `extends` clause of a class that extends `ArrayList` or another implementation type (#689).
- `ward:java/reliance-on-default-charset` reports `getBytes()` only on `String`, not on any type with a method of that name (#691).
- `ward:java/uwf-unwritten-field` counts a write through another instance, as in a static factory, as a write of the field (#692).

## [0.11.2] - 2026-11-20

### Fixed

- `ward:java/openjdk-annotation-location` no longer reports a parameter list whose parameters start with annotations and sit on separate lines (#684). The rule now counts only annotation and modifier nodes, so the layout `ward format` writes passes.

## [0.11.1] - 2026-11-19

### Fixed

- `ward:java/package-case` no longer reports a lowercase package whose declaration carries an annotation, such as `@NullMarked` or `@Deprecated` in `package-info.java` (#674). The rule now reads only the qualified package name, and reports it at the name instead of at the start of the declaration.
- `ward:java/package-case` leaves the `standard` and `strict` presets (#674). It now reports the same place as `ward:java/package-name`, which stays and also finds every uppercase package name, so a preset reports an uppercase package once instead of twice (as #599 does for other duplicates). It stays in the catalog and can be selected with `include`.

## [0.11.0] - 2026-11-18

### Added

- Agent prompts that contain a secret are blocked before they are sent (#621). `ward init --prompt-hook` and `ward integrate --prompt-hook` add the prompt event to the Claude Code, Codex, Cursor, Gemini and Factory Droid hooks and write `[checks.secrets.prompts]`; the `ward:secrets` rules scan the message and its attached files, and a prompt is blocked when the scan fails for any reason, with a message that names no prompt content. The `[checks.<check>.prompts]` table is generic, and only `secrets` supports it: a `prompts` table on another check is a configuration error.
- `ward init --shared-hooks[=<dir>]` writes the quality hooks into a committed folder (`.githooks` by default) and points `core.hooksPath` at it, also on an already configured project, so every clone runs the same checks (#619). `--remove-quality-hooks` honours `--dry-run`, and `ward doctor` warns when `core.hooksPath` names a missing folder.
- Java taint follows calls on an interface or abstract receiver to its single project implementor, and values through the fields of one class (#603).
- Taint support for Swift (#605). Swift bodies are lowered to the flow IR, and three taint rules follow a URL query or a server request to a sensitive call: `ward:swift/command-injection` (`Process`, `system`), `ward:swift/sql-injection` (SQLite C API, FMDB, SQLite.swift, GRDB) and `ward:swift/path-injection` (`FileManager`, `FileHandle`, `fopen`). A sanitizing member such as `lastPathComponent` can now be named in a taint specification.
- TypeScript security rules for insecure AWS CDK settings (#606): `ward:typescript/cdk-public-bucket`, `cdk-unencrypted-storage`, `cdk-open-security-group` and `cdk-wildcard-iam-policy`.
- `ward:python/argument-injection` (CWE-88) reports a tainted value passed as an option to a command (#636).
- Kotlin taint tracks `this` and implicit receivers in scope functions and infers receiver types from the declarations of the file (#635).

### Changed

- A preset never reports the same place twice for the same reason (#599): 113 rules that duplicate another rule leave the presets and stay in the catalog (`presets/duplicates-dropped.txt`).
- Swift rules follow Swift conventions instead of Java ones (#596). `constant-name` expects lowerCamelCase and `class-type-parameter-name` and `method-type-parameter-name` expect UpperCamelCase names, as the Swift API Design Guidelines name them. `hidden-field` also leaves the parameters of an initializer with modifiers (`public init(value:)`). `naming` no longer reads `case NAME:` of a switch as a declaration, `custom-import-order` compares only imports of the same `#if` branch, `large-tuple` skips the payload of an enumeration case, `google-right-curly` skips the `else` of a multi-line `guard`, and `for-where` needs the `if` to be the loop body itself. `hard-coded-credential` checks every constant of a declaration with several.
- The native binary uses the `BySpaceAndTime` collector policy, with no heap size built in (#601). On this repository a cold `ward` check with one job spends about 40 s instead of 365 s in garbage collection and finishes about 36 % sooner; peak memory with one job rises from 2.4 GB to 4.0 GB.
- Faster cold analysis (#602, #502): the taint rule rebuilds no graph and parses related bodies once (about 48 % fewer instructions per cold run), the dependency step parses only the languages a selected rule reads, the `forbidden-node` rules of a file share one tree walk, and quick prediction covers more TypeScript constructs and JavaScript (parse time about 12 % lower on `got`, about 30 % on JavaScript files). Findings are unchanged.
- The analysis cache keys a file on its text and the taint summary of the files it calls, instead of the text of its whole dependency closure (#604): an edit elsewhere that keeps the summary keeps the cached result. A cold run computes the summary once more, which costs roughly 20 to 60 % more instructions.
- Native checks report their measured duration (#634).
- `api-compat` reads the baseline revision through one `git ls-tree` and one `git cat-file --batch` process instead of one `git` process per file (#667), and reads only declarations, without method bodies (#670). On this repository the gate goes from about 72 s to 4 s and its peak memory from 2.7 GB to 1.4 GB, with the same findings. A syntax error inside a method body is no longer reported by `api-compat`.
- This repository is formatted by `ward format` in every language and runs the corpus smoke tests in CI (#608, #600). Its pre-push skips the test gates when a push changes no code or file a test reads.

### Fixed

- The native binary without `JAVA_HOME` finds the JDK on `PATH`, through `/usr/libexec/java_home` on macOS or the running JDK; with none, the Java and Kotlin `TYPES` rules are reported as not run and report no finding, so results no longer depend on the environment (#593).
- Python rules report fewer false positives on web applications (#597). `naming` accepts the unittest names (`setUp`, `tearDown`, ...); `unused-method` knows the methods Django calls by name (`clean_<field>`, admin, command and URL-converter hooks); `redefined-builtin` ignores a class attribute such as `help`; `hardcoded-temp-file` skips test files; `constant-name` skips `*.conf.py` and no longer reads a keyword argument of a call as the assignment; `redefined-while-unused` and `unused-import` accept `import a` followed by `import a.b` and the two branches of one `if` or `try`; `used-before-assignment` and `undefined-loop-variable` no longer read the variable of a comprehension as a read of the function; `unused-argument` reads names in f-strings; `type-name` accepts a leading underscore; `too-few-public-methods` skips a `Meta` class. The generic `identifier-case` measure takes `exempt-names`, and `forbidden-node` takes `not-parent-path`. CTFd (Flask) is measured by the new `FlaskCorpusSmokeTest`.
- Java rules report fewer false positives on real code (#627). A field that a method called by the constructor, or a `@BeforeEach`-style method, sets is initialized (`uwf-field-not-initialized-in-constructor`). A zero-length array field is not exposed state (`ei-expose-rep`, `method-returns-internal-array`, `ms-expose-rep`). A project's own `setAccessible` helper is not reflection (`avoid-accessibility-alteration`). A class with an `extends` clause is not reported for a transient field or a missing `Cloneable` (`se-transient-field-of-nonserializable-class`, `clone-method-must-implement-cloneable`, `cn-implements-clone-but-not-cloneable`). `equals` of a subclass of `Number`, `Thread` or `Format` is not asymmetric (`eq-overriding-equals-not-symmetric`). A `long` compared with the `int` limits is a range check (`int-vacuous-comparison`). A parameter given a default value is not a lazy singleton (`non-thread-safe-singleton`). `else`, a comment and `if` is an else-if chain (`control-statement-braces`). `test_clone` and `test_equals` are not `clone` and `equals` (five clone and equals rules). `assert-statement-in-test` reads test sources only, and the one- and two-finding rules of #581 are tightened (see the pull request).
- Kotlin rules report fewer false positives on real code (#628): backticked names and factory functions in `property-name` and `function-name`, spreads of an own vararg in `spread-operator`, extensions as overloads only of the same receiver, and `actual`/`expect` classes in `utility-class-with-public-constructor`.
- TypeScript and JavaScript rules report fewer false positives on real code (#598): scope analysis reads type parameters of call signatures and function types, `infer` and mapped-type variables, destructuring and overload signatures, and `no-unsafe-finally`, `use-global-this`, `no-useless-escape`, `no-unused-template-literal`, `no-sparse-arrays`, `member-name`, `require-await` and `cleartext-transport` are fixed.
- The Java formatter keeps comment spacing and chain continuation, and keeps the member indent of a generic declaration that follows a comment (#629, #640).
- `ward format` run from a subdirectory finds the project root and formats only the files below it (#637).
- The TypeScript grammar accepts `readonly` parameter properties.
- A TypeScript or JavaScript function named with a contextual keyword such as `global` or `async` no longer ends the analysis with a `NullPointerException` (#638).
- Python SQL sinks are matched by the scope facts of the receiver, so a cursor stored under another name is a sink (#636).

### Removed

- `ward:swift/unused-imports` and `ward:swift/missing-switch-default` leave Swift (#596): Swift code does not write the module name it uses, and the compiler rejects a switch that is not exhaustive.

### Known issues

- A cold run of this repository does not fit a 2 GB heap with one job (#641).
- A cold run with `--jobs auto` computes the cache keys of #604 on one thread, so on many cores it can take about three times longer than 0.10.0 (on this repository 110 s against 330 s); with `--jobs 1` it costs about 48 % more instructions (#668).

## [0.10.0] - 2026-11-16

### Added

- `ward-architecture.toml`: the `external` selector names code outside the project, so "the domain does not import the framework" is one rule (#570). `must-not-depend-on = { external = ["org.springframework"] }` reports every import by a selected file of that package or module name, or of a name below it after `.` or `/`, that resolves to no project file. The finding has the id `ward:architecture/dependencies` and sits at the import, in Java, Kotlin, TypeScript, JavaScript, Python and Swift. An entry is a name without `*`; `external` in `select` or in another constraint is a configuration problem.
- `ward init` writes a starter `ward-architecture.toml` when you choose the architecture style (`--architecture`, or the wizard's question) and the project has none: one rule for the style that the `layers` table does not state, with `allow-empty = true`, and a commented `external` example (#570). `--dry-run` shows the file. After that the file is yours: an init that finds it, edited or not, leaves it byte for byte and shows it as kept. An init with the default style writes no file.
- `ward check --jobs <n>` runs up to `n` checks at once, and `--jobs auto` one per processor; the environment variable `WARD_JOBS` sets the same value for Git hooks and CI (#577). `ward-format`, `secrets`, and `ward` without `java-classpath` run next to any other check; every other check still runs one at a time in the order of `ward.toml`. The report, the order of the checks and the exit code do not depend on the value. Without the option nothing changes.
- With `--jobs` (or `WARD_JOBS`) above 1, the `ward` check analyzes several files at once (#585): parsing and the per-file rules run on up to that many threads. The value is one limit for the whole command: a running check and a file under analysis each count as one job. Findings, their order, execution errors and the `.ward` cache do not depend on the value. Without the option the `ward` check works on one thread as before. More jobs need more memory per file under analysis, and less in total than one job on a large project, because the syntax trees of a file are dropped when its rules are done.
- Swift security rules (`cleartext-url`, `weak-hash`, `weak-cipher`, `weak-random`, `hard-coded-credential`, `userdefaults-secret`) report the column of the offending expression: `text-pattern` checks take an opt-in `position = "match"` (#559).

### Changed

- The `ward` check runs its per-file rules on a thread with a stack of 8 MiB, whatever the value of `--jobs` (#585). A file on which a rule ran out of stack on a JVM with the default stack (1 to 2 MiB) can now be analyzed; no other result changes.
- `android-lint` stays an external gate for good (#571): the checks it adds over the native `ward:android/*` rules need resolved types, the merged manifest and resources, Gradle's model or API level tables, and the table in the Android SDK is under a licence that forbids redistribution. The website page of the gate gives the reasons.
- This repository checks its own Java with the `ward` and `ward-format` gates instead of the Checkstyle, PMD, SpotBugs and Spotless Maven plugins (#347, #470). Its sources are formatted with `ward format`.
- The cold analysis is about twice as fast on this repository (427 s to 215 s for the `default` profile, `secrets` 121 s to 21 s, jar), with identical findings (#569): the path allowlist of the `secrets` check is cached per path, the taint rule skips functions with no source or no sink site, and `forbidden-node` tests its structural conditions before it builds the node text.
- Parsing TypeScript is about three to four times faster: the decisions that read hundreds of tokens of lookahead are answered from one or two tokens, with the same syntax trees (#568, #502).
- Parsing Swift is about 100 times faster on real code: the grammar no longer reads to the end of a block at almost every brace (#567).
- Python SQL sinks (`execute`, `executemany`, `executescript`, `mogrify`) match a DB-API cursor or connection receiver only, so `executor.execute` or `pipeline.execute` are not sinks; a cursor stored under another name is not recognized yet (#559, #607). A `subprocess` argument vector without `shell=True` is not command text.

### Fixed

Ward was run on real code in every language, and every fix below adds a case to the rule's fixture. The corpora are only measured, never committed: Apache Commons Lang 3.22.0 and Spring PetClinic for Java (#564), Okio 3.18.2 and Spring PetClinic Kotlin for Kotlin (#565), Healthchecks 5.0.3 for Python (#566), Alamofire 5.12.2 and the Food Truck sample for Swift (#567), and got 16.2.0 next to OWASP Juice Shop for TypeScript and JavaScript (#568). No valid file of any corpus is left without analysis.

- **Files left without analysis.** Swift: 104 of 183 files had syntax diagnostics (`#if` conditions and around cases, keywords as member names, `@Sendable` and `async` function types, `where Self == T`, `A & B`, and more). Kotlin: 27 of 313 Okio files (keywords inside parentheses, lambdas inside parentheses and string templates, unsigned literals, anonymous functions); a class body after a supertype call was read as a trailing lambda. TypeScript: 22 of 96 `got` files (type parameters with a constraint and a default, `override`, `declare`, generic arrow functions, `let x!: T`, mapped types, `||=` and `&&=`, `export type *`, legacy `"\1"` escapes). Java: a string literal of a few thousand characters overflowed the stack in 12 checks; a rule that runs out of stack is now recorded as an error of that rule on that file instead of ending the run. Python: two rules backtracked exponentially on a dictionary literal and never ended.
- **`ward format`.** Swift: `as? T`, `as! T` and `try? x` were written with a detached mark (invalid Swift), offsets broke after an emoji, and 40 corpus files were refused; now none. Python: every file that ends with a blank line was refused. TypeScript and JavaScript: files with legacy octal escapes were refused. Java: a comment right after an opening parenthesis was not stable.
- **Java rules** with false positives on the corpus (findings before → after): `ase-assertion-with-side-effect-method` and `ase-assertion-with-side-effect` (2,405 and 396 → 0, `assertEquals(...)` read as `assert`), `il-infinite-recursive-loop` and `infinite-recursion` (341 and 334 → 0, a call to an overload), `eq-other-no-object`, `use-short-array-initializer`, `non-runtime-annotation`, `dmi-annotation-is-not-visible-to-reflection`, `ms-mutable-array`, `fall-through`, `sf-switch-fallthrough`, `test-class-without-test-cases`, `nm-method-constructor-confusion`, `j-unit-spelling`, `loop-condition-checker`, `unsynchronized-static-formatter`, `sa-local-self-computation`, `unit-test-should-use-test-annotation`, `invalid-javadoc-position`, `dont-use-float-type-for-loop-indices`, `dmi-calling-next-from-hasnext`, `dep-ann`, `missing-deprecated`, `wa-not-in-loop`, `mwn-mismatched-wait`, `rp-c-repeated-conditional-test` and `logic-inversion`.
- **Kotlin rules**: `unused-local-variable` (119 → 0), `javadoc-paragraph` (590 → 1, KDoc is Markdown), `var-could-be-val`, `unnecessary-parentheses-before-trailing-lambda`, `invalid-javadoc-position`, `kdoc`, `unused-parameter`, `property-name`, `variable-declaration-usage-distance`, `double-mutability-for-collection`, `exception-raised-in-unexpected-location`, and a stack overflow in `use-require`. A `text-pattern` rule honours `not-inside`.
- **Python rules**: `hardcoded-password-func-arg` (707 → 0), `method-name` (unittest names), `useless-return`, `single-line-implicit-string-concatenation`, `one-statement-per-line`, `wrong-import-order`, `using-constant-test`, `unused-loop-control-variable`, `unexpected-special-method-signature`, `invalid-first-argument-name-for-method`, `no-method-argument`, `import-shadowed-by-loop-var`, `useless-comparison`, `untrusted-redirect` (new sanitizers `urlencode`, `quote_plus`, `reverse`, `url_for`), `weak-password-hash`, `unpacking-non-sequence`, `unsupported-membership-test`, `static-key-dict-comprehension`, `function-redefined`, `unnecessary-dict-index-lookup`, `unnecessary-list-index-lookup`, `unintentional-type-annotation` and `useless-expression`. `too-few-public-methods`, `too-many-instance-attributes` and `too-many-ancestors` print their numbers instead of `{value}`.
- **Swift rules**: `summary-javadoc` (892 → 3), `abbreviation-as-word-in-name` (1,126 → 453), `javadoc-method`, `unused-closure-parameter`, `unused-local-variable`, `one-statement-per-line`, `identifier-name`, `method-name`, `google-method-name`, `unused-parameter`, `member-name`, `constructors-declaration-grouping`, `control-statement`, `catch-parameter-name`, `left-curly` and `parameter-name`. `hard-coded-credential` needs the name to end with the credential word, so `tokenLabel` or `passwordHint` are not reported (#559).
- **TypeScript and JavaScript rules**: `one-statement-per-line` reads the syntax tree (250 → 0), `adjacent-overload-signatures` and `overload-methods-declaration-order` (96 → 0, getters), `getter-return`, `no-undef-init`, and names used only inside an optional chain are read.
- **Kotlin taint**: the first argument of `with` is the receiver of the lambda, so `with(tainted) { Integer.parseInt(this) }` is not reported (#559).

## [0.9.1] - 2026-11-15

### Added

- `ward:java/coupling-between-objects` reports a class coupled to more than `max` (default 20) distinct types through its declarations: the types of fields, local variables, parameters, loop variables, resources and return types, without primitives and `java.lang` types (#347).

### Changed

- Apache Fory is updated from 1.7.7 to 1.7.8. Its native-image feature now ignores the JSON codecs that the annotation processor generated, including those packaged in dependencies (apache/fory#4146). Ward's native-image configuration no longer lists the generated codecs, Fory's internal types or the packages to initialize at build time.

### Fixed

- `ward:java/collection-incompatible-type` no longer reports `List<UUID>.get(int)`: `get`, `getOrDefault`, `containsKey` and `containsValue` apply only to `Map` types and `indexOf` and `lastIndexOf` only to `List` types (#573). A `methods` entry of `incompatible-argument` may end with `@key` to name the declaring types it applies to.
- `ward:java/infinite-recursion` and `ward:java/il-infinite-recursive-loop` no longer report `return new Customer(...)` in a method named `newCustomer`: the check compares tokens, not the text of the statement without whitespace (#573).
- `ward:java/j-unit4-test-not-run`, `j-unit4-set-up-not-run` and `j-unit4-tear-down-not-run` report only a file that uses JUnit 4 (an import of `org.junit` other than Jupiter, or of `junit.framework`) and stay silent when it imports `org.junit.jupiter`; a public `test...` method with parameters is never a missing `@Test` for `unit-test-should-use-test-annotation` either (#573).
- `ward:java/law-of-demeter` trusts a call that returns the type of its receiver (builders), a receiver or call result whose type is in `java.` or `javax.` (`Optional`, streams, strings), a record receiver and a getter without arguments. The new option `trusted-types` lists patterns of further types to trust (#573).
- `ward:java/static-or-default-interface-method`, which is about Android before 7.0, moves from the `standard` preset to `strict`: a rule sees one file and not the project, so it cannot tell an Android project from another (#573).
- `ward:java/cwo-closed-without-opened` no longer reports `lock.unlock()` in a method that calls a method of the same file that acquires that lock, such as a private `tryLock()` (#573).
- A `forbidden-node` rule whose pattern starts with a keyword (`^new(...)`, `^if(`, `^for`, ...) now needs that keyword as the node's first token, so a call to `newBoolean(...)` or `iffy(...)` is no longer read as `new Boolean(` or `if(`. This stops `ban-class-loader`, `dead-thread`, `dm-boolean-ctor`, `dm-number-ctor`, `dm-string-ctor` and `dp-create-classloader-inside-do-privileged` from reporting calls to methods named `newX` (#573).
- `ward:java/control-statement-braces` decides on the statement's syntax nodes instead of its text, so a call such as `document.parts()` or `doStatement(...)`, and a string with a parenthesis such as `endsWith(")")`, are no longer reported as a body without braces.
- `ward:java/while-loop-with-literal-boolean` reports `while (false)` and `do ... while (true)` or `while (false)`, as PMD does, and no longer a plain `while (true)`.
- `ward:java/for-loop-can-be-foreach` reports only a loop that starts at `0`, whose element is not assigned, and whose `.get(i)` receiver is known to be an `Iterable` (an array needs no type); `JsonTree.get(i)` and `array[i] += x` are no longer reported. The rule now has the `TYPES` capability.
- `ward:java/logic-inversion` no longer reports `!(x instanceof Map<?, ?> m)`, whose `<` and `>` are type arguments.
- `ward:java/confusing-ternary` no longer reports `if (x != null) ... else ...`, as PMD does not.
- `ward:java/labeled-statement` no longer reports a statement that starts with a method reference such as `Type::method`.
- `ward:java/premature-declaration` stops at a statement with a call or a creation that does not throw, as PMD does, and no longer reads the words `return` and `throw` inside string literals and text blocks.
- `ward:java/assignment-in-operand` ignores `=`, `++` and `--` inside string and character literals of the condition.
- `ward:java/avoid-branching-statement-as-last-in-loop` decides on the last statement's syntax node, so a call such as `returnsOf(x);` is no longer taken for a `return`; it also covers `do` loops.
- `ward:java/preserve-stack-trace` reports only `throw new ...` in a catch block, not the rethrow of an existing exception, and a nested catch clause answers for its own throws.
- `ward:java/use-try-with-resources` reports `x.close()` in a `finally` block only when the type of `x` is known to be `AutoCloseable`; an `XMLStreamReader` is no longer reported. The rule now has the `TYPES` capability.
- `ward:java/avoid-throwing-new-instance-of-same-exception` reports only `throw new X(e)` with the caught exception as the sole argument; `throw new X("context", e)` adds context and is not reported.
- `ward:java/j-unit-use-expected` no longer reports a `try` that calls `cursor.fail(...)` or another method named `fail` on an object; only `fail(...)` and `Assert.fail(...)`.
- `ward:java/unit-test-should-include-assert` counts a call to a method named `check...`, `verify...` or `fail` as an assertion, as PMD does.
- `ward:java/do-not-terminate-vm` does not report `System.exit` in a method named `main`, as PMD does.
- `ward:java/unnecessary-warning-suppression` no longer reports a Javadoc comment that only names `NOPMD`.
- `ward:java/test-class-without-test-cases` no longer takes the interface named after `implements` or `extends` for the class's own name (`class Facts implements FactTest`).
- `ward:java/hide-utility-class-constructor` no longer reports a class that extends another class (an exception class), a private class, or a class with an inner class (#347).
- `ward:java/instantiable-utility-class` no longer reports a private nested class, which no other code can construct (#347).
- `ward:java/class-fan-out-complexity` no longer counts the class itself, the types named only as type arguments (`List<Row>`), or the types of a nested record, interface or enum, which are counted for that type (#347).
- `ward:java/data-class` takes the weight of class over the non-private methods, static ones included, as PMD does, and counts `&&` and `||` in its complexity only inside a condition (#347).
- `ward:java/god-class` counts `&&` and `||` in its complexity only inside a condition, as PMD does (#347).
- `ward:java/abstract-class-without-abstract-method` no longer reports an abstract class that extends or implements a type (#347).
- `ward:java/implicit-functional-interface` no longer counts a `static` or `private` method as abstract when its modifier sits before an annotation, and leaves an interface that extends another one, whose inherited methods are unknown (#347).
- `ward:java/override-both-equals-and-hashcode` reads `equals(final Object other)` and no longer matches the text of a string literal (#347).
- `ward:java/missing-static-method-in-non-instantiatable-class` no longer reports a nested class, which its enclosing class can construct (#347).
- `ward:java/suspicious-hashcode-method-name` reports only a method whose whole name is `hashCode` in some letter case, not a longer name that ends in `HashCode` (#347).
- `ward:java/more-than-one-logger` counts logger fields, not logger variables in a method (#347).
- `ward:java/method-with-same-name-as-enclosing-class` compares a method name with the class name as written, as PMD does; a field name is still compared ignoring case (#347).
- `ward:java/linguistic-naming` no longer reports a `void` method named `is...`, `has...`, `can...`, `have...`, `will...` or `should...`, which PMD accepts (#347).
- `ward:java/use-varargs` no longer reports an array parameter that is not the last one, a `byte[]` parameter or `main(String[])`, as PMD UseVarargs does.
- `ward:java/loose-coupling` no longer reports a constructor reference such as `TreeSet::new` or a class literal such as `ArrayList.class`.
- `ward:java/unnecessary-varargs-array-creation` reports an array only when the called method is varargs and the array has the type of the variadic parameter; it needs type facts now (`capability = "TYPES"`).
- `ward:java/internal-api-usage` no longer reports the `com.sun` packages that a JDK module exports as supported API, such as `com.sun.net.httpserver`, `com.sun.management` and `com.sun.source`.
- `ward:java/uc-useless-object` no longer reports a collection whose `add` result is used, as in `if (seen.add(x))`.
- `ward:java/lambda-can-be-method-reference` no longer reports a lambda that only creates an object, which PMD does not report.
- `ward:java/reliance-on-default-charset` no longer reports `new InputStreamReader(new GZIPInputStream(in), charset)`: the charset after a nested call counts as passed.
- `ward:java/unused-return-value` no longer reports the value of a `case X -> value;` of a switch expression.
- `ward:java/enum-comparison` reports an `equals` argument only when the argument itself is an enum, not when an enum is nested in it, as in `equals(List.of(RED))`.
- `ward:java/double-brace-initialization` no longer reports the text of a string literal that looks like `new X() {{`.
- `ward:java/string-instantiation` no longer reports `new String[] {f(text)}`: only the arguments of a class creation count.
- `ward:java/inefficient-string-buffering` reports only calls of `StringBuilder` and `StringBuffer` (it needs type facts now), not an `append` of another type.
- `ward:java/replace-enumeration-with-iterator` reports a class, enum or record that implements `Enumeration`, as PMD does, not each use of the type.
- `ward:java/compare-objects-with-equals` no longer reports `this == other` inside a method named `equals`.
- `ward:java/use-index-of-char` reports only `String.indexOf` and `lastIndexOf`, not `List.indexOf("/")`; it needs type facts now.
- `ward:java/constants-in-interface` leaves an interface that declares a method alone, as PMD's `ignoreIfHasMethods` does.
- `ward:java/explicit-initialization` skips `final` fields, as Checkstyle's `ExplicitInitialization` does.
- `ward:java/unused-local-variable` no longer reports the binding of a `case` pattern such as `case Str str -> 1;`, which Checkstyle's `UnusedLocalVariable` does not support either.
- `ward:java/uwf-field-not-initialized-in-constructor` has the severity `note`: SpotBugs always reports `UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR` at low priority, so the Medium threshold never shows it.
- `ward:java/singular-field` skips fields a picocli annotation fills, requires that the method assigns the field unconditionally before it reads it, and skips a field the enclosing class of a nested class mentions.
- `ward:java/immutable-field` skips a private field of a nested class that the enclosing class assigns, as PMD reads every use in the file.
- `ward:java/missing-serial-version-uid` reports only a class that implements `Serializable`, not a class whose name contains it.
- `ward:java/hidden-field` does not report a parameter where no instance of the class is in reach: in an anonymous class of a static method, or in a nested interface, enum or record.
- `ward:java/null-assignment` skips the assignment of `null` to a `final` field, as PMD does.
- `ward:java/si-instance-before-finals-assigned` reports only when a constructor or instance initializer reads the static final field assigned late, as SpotBugs does.
- `ward:java/array-is-stored-directly` skips private methods and constructors, as PMD's default `allowPrivate` does.

## [0.9.0] - 2026-11-14

### Added

- `api-compat` compares a multi-module Maven or Gradle build module by module (#560). It discovers the modules of the build at the baseline and in the working tree (Maven `<modules>`, recursive and with profile modules; Gradle `include(...)` of `settings.gradle(.kts)`), reads each module's main roots from its own build file, and names the module in each finding (`[core] p.Client#close: ...`). A new module is not an error, a removed or renamed module reports its types as `type-removed`, and a type that keeps its name but moves to another module is reported as the new `api-compat:type-module-changed`. The new option `modules = ["core"]` limits the compared API to the named module directories. A project with no listed module behaves as before. Gradle `projectDir` remapping, `includeBuild` and `include` calls built from variables or split over lines are not read.
- Kotlin taint analysis: Kotlin gets a control-flow lowering (`fun`, secondary constructors and `init` blocks, with `if`, `when`, loops, `try`, string templates, safe calls, `?:`, named and default arguments, and lambdas kept inside their statement) and the `CFG`, `DATAFLOW` and `TAINT` capabilities, so the eighteen injection rules of Java now run on Kotlin as `ward:kotlin/*`: `sql-injection`, `command-injection`, `path-injection`, `cross-site-scripting`, `untrusted-redirect`, `server-side-request-forgery`, `ldap-injection`, `xpath-injection`, `log-forging`, `header-splitting`, `untrusted-deserialization`, `script-injection`, `expression-injection`, `jndi-injection`, `unsafe-reflection`, `regex-injection`, `environment-injection` and `session-attribute-injection`. They read Servlet requests, Spring `@RequestParam`-style parameters, Ktor calls and `readLine()`, follow values between functions and between Kotlin and Java files (a call in another file names the class: `Helper().run(x)`), and the `standard` and `strict` presets select them as they do for Java (#539).
- Kotlin taint analysis follows more Kotlin: a lambda has its statements in blocks of its own, so a branch inside it keeps its paths apart, `it` and a named parameter carry the receiver's taint, and a sanitizer that is the last expression of `let`, `run`, `with`, `use` or `synchronized` cleans its result (`also`, `apply`, `takeIf` and `takeUnless` give back the receiver); an `if`, `when` or `try` that makes a value joins its branches, and `&&`, `||` and `?:` are branches; `request.parameterMap` is also the call of the Java getter `getParameterMap()`, and a property's initializer, delegate, getter and setter are analysed as bodies; a `val` that holds a new object has that type, and a call on it pairs with the class in another file (#559).
- Java taint analysis follows more flows (#539, #559): through chained receivers such as `response.getWriter().println(value)`; across files, into the methods of the classes a file depends on, with the finding at the call; through a variable, field or parameter whose class is decided; and through a container, array or object that received a tainted value. Two classes with the same simple name are told apart by package. A call with several possible implementations is not followed. `ward:java/hard-coded-password` and `ward:java/cookie-missing-secure` are new, and `ward:java/hard-coded-crypto-key` is now a taint rule that reports where the key is used.
- Python taint rules (#539): `ward:python/sql-injection`, `command-injection`, `code-injection`, `untrusted-deserialization`, `path-injection`, `untrusted-request-url`, `untrusted-redirect` and `template-injection`, with Flask, Django and FastAPI request values as sources.
- The first Swift security rules (#539): `ward:swift/cleartext-url`, `weak-hash`, `weak-cipher`, `weak-random`, `hard-coded-credential` and `userdefaults-secret`. They match text patterns and report a line.
- `ward-architecture.toml`: architecture rules that you write once, with the same text for Java, Kotlin, TypeScript, JavaScript, Python and Swift. A `[[rule]]` selects files (`paths`, `directories`, `exclude-paths`), states one constraint (`must-not-depend-on`, `may-only-depend-on`, `only-accessed-by`, `file-name` or `must-be-in`) and gives a `because` reason; the finding starts with the rule's `name` and ends with its reason. Findings carry the new ids `ward:architecture/dependencies` (at the import that creates the dependency), `ward:architecture/naming` and `ward:architecture/location`. The file is read from the project root when it exists, and `architecture-file` on the `ward` check names another project-relative path. A rule that selects no file (unless `allow-empty = true`), and a dependency rule over a language with no dependency edges in the run (Swift outside a SwiftPM or Xcode project), fail the gate instead of passing. The file holds no commands, so reading it runs nothing, and `ward init` neither writes nor changes it. The schema is published as `ward-architecture.schema.json`. `layers`, `cycles` and `independence` stay in `ward.toml` with their ids and messages.
- Native Android rules, `ward:android/*`, that run in the `ward` gate over `AndroidManifest.xml` and the XML files under `res/` (`layout`, `menu`, `xml`, `values`, `drawable`, `navigation`, `color`), with no Android SDK and no Gradle run: `exported-service`, `exported-receiver` and `exported-content-provider` (an exported component with no permission), `debuggable-application`, `cleartext-transport` (the manifest flag and `cleartextTrafficPermitted` in a network security config), `allow-backup`, `hardcoded-text` and `missing-content-description`. They follow the ids and documented behavior of Android Lint (Apache-2.0); the `standard` and `strict` presets select them. The `android-lint` gate stays for the checks that need the compiled platform API, such as `NewApi`. `tools:ignore` is not honored: turn a rule off in `ward-rules.toml`. `ward:android/unused-resources` reports a string, color, dimension, style or other value, and a layout, menu, drawable, color, navigation or XML file, that no XML file names with `@type/name` and no Java or Kotlin file names as `R.type.name` (a layout also counts as used when its view binding class is named); a resource read only through `getIdentifier` is reported. `AndroidLintParitySmokeTest` runs the real Android Lint over the fixtures; the exported-provider rule now treats a read or a write permission as protection, as Lint does (#550).
- `ward:<language>/duplicate-code` gains the option `max-duplicated-percent` (default `100`, off): when the tokens inside copies, each counted once, exceed that share of the compared tokens of the language, one more finding reports the project (#547). The rule already had `min-tokens`, findings that list every copy, and Java, Kotlin, TypeScript, JavaScript, Python and Swift support. See [duplicate code](website/src/content/docs/configuration/ward-rules.mdx).
- `ward sbom --name <name>` sets the name of the root component.

### Fixed

- The TypeScript frontend parses valid code written without semicolons (#562). `return g(x)` before a closing brace of an arrow function, interface and type-literal members and class properties on separate lines, `x as T[]`, `x as [A, ...B[]]`, generic calls such as `signal<T[]>([])`, class members such as `static async f()`, `async *g()`, `declare x?: T` and `x!: T`, `.is(...)` calls, and a template literal inside an object literal inside a template substitution no longer report "the unit has syntax errors", so the `ward` gate now analyses those files. Contextual keywords (`satisfies`, `accessor`, `get`, `set`, `using`, `asserts`, `infer`, `out`, `global`, `is`) are lexer tokens that an identifier may still be, because a parser predicate that is not at the start of a rule is invisible to prediction. On OWASP Juice Shop v20.4.0, 151 of 681 files were not analysed; now 30, all code snippets that are not complete units.
- `ward:architecture/layers` now checks Kotlin files: a Kotlin import of a `forbidden` layer from a `from` layer is reported, in Kotlin-only and mixed Java and Kotlin projects, and Kotlin sources under `src/test` are skipped as Java ones are. The rule and the graph rules declared the `SYMBOLS` capability, which the Kotlin frontend does not provide, so Kotlin could not be added to their languages; they now declare `SCOPES`, the capability they read dependency edges through. `ward:architecture/cycles` and `ward:architecture/independence` already reported Kotlin files, because they run over the whole dependency graph (#558).

### Changed

- The `layers` section of the gates page no longer says that Swift is not covered: the rule has covered Swift since 0.6.0.
- **Breaking (alpha):** The `sonar` gate is removed. `ward init` no longer offers it and writes no `sonar-maven-plugin` entry and no `org.sonarqube` Gradle plugin; `--gate sonar` and `uses = "sonar"` are rejected as an unknown gate, and a check whose command runs `sonar:sonar` or `sonarqube` is a plain command check. The plugin entry in your build file is yours to remove. Migration: select `ward:<language>/duplicate-code` and the `ward` and `crap` gates for duplication, style and complexity. Ward does not replace SonarQube's history, dashboards or pull request decoration (#547).
- **Breaking (alpha, no migration):** the `zap` gate type is removed, the way `semgrep` is, and Ward offers no dynamic scanning (DAST). A `ward.toml` with `uses = "zap"` is a configuration error (exit `2`) that lists the supported types, and `ward init --gate zap` fails as an unknown gate. A check whose `command` runs `zap-baseline.py` or another ZAP script is a plain `command` check: Ward reports its exit status, extracts no findings, gives no install guidance and no longer requires a `-t` target. Also removed: the `<TARGET_URL>` placeholder, the init option, the install-catalog entry, the doctor probe and the command type inference. Static security rules of the `ward` gate cover cookie flags, CSRF, CORS, clear-text transport and certificate checks in code. The security page of the docs now lists the network requests Ward makes: the update check and the OSV download of `vulnerabilities`.
- The root component of `ward sbom` is named after the project (Maven `artifactId`, Gradle `rootProject.name`, `package.json` `name`, `pyproject.toml` project name, `Package.swift` package name) instead of the folder, so the same project gives the same SBOM in any checkout. A project that declares no name keeps the folder name. `ward licenses` never used the folder name.

## [0.8.0] - 2026-11-13

### Added

- Native OpenAPI lint rules, `ward:openapi/*`, for OpenAPI 3.0 and 3.1 documents in YAML or JSON: the OpenAPI 3.x rules of Spectral 6.17.1 (`spectral:oas` with every rule) and of its OWASP ruleset 2.0.1, with Spectral's severities, positions and messages, a JSON Schema validator for the document and the examples, and `$ref` resolution inside the project (#522). `ward rules list` shows them, and `ward-rules.toml` configures them.
- The `secrets` check (`uses = "secrets"`), native secret detection that needs no executable: the 222 rules of the Gitleaks 8.30.1 default configuration run inside Ward as `ward:secrets/<gitleaks-rule-id>`, over every text file, in the fast profiles like `ward-format`. Findings carry the rule, file, line and column and never the secret or any source text. Options: `exclude-paths`, `scan-history` and `history-since` on the check, and `entropy` and `exclude-paths` per rule in `ward-rules.toml` (#516).
- `ward check` text output shows what the `ward` gate checks: one result line per rule category (`✓ ward · style`, `✗ ward · correctness — 3 errors`) instead of the single `ward` line, and on an interactive terminal the running line names the current phase (`… ward: analyzing 12 of 812 files with 431 rules`). JSON and SARIF output are unchanged.
- The `crap` check (`uses = "crap"`) scores every function as `complexity^2 * (1 - coverage)^3 + complexity` and fails the functions above `max` (default 30). Complexity is the value of the language's complexity rule; coverage is read per line from a JaCoCo XML or lcov report, by default at the build tool's conventional path, or from `report`. The gate runs no tests, treats a missing, unparseable or stale report as an execution error, and is opt-in.
- `ward init --gate crap` offers the `crap` gate for Maven, Gradle, Vitest, Jest, Bun and Python projects, after the gate that produces the coverage data. No preset selects it. It is not offered for Swift, Android and Angular (`ng test`) projects.
- The native `vulnerabilities` check (`uses = "vulnerabilities"`): it matches the resolved dependencies of every toolchain against OSV advisories, with the version ordering of Maven, semver and PEP 440. Options `minimum-severity` and `max-age-days`. A reduced copy of each advisory database lives under `.ward/cache/vulnerabilities/`. `ward check --offline` makes no network request, and `ward vulnerabilities update` refreshes the databases (#519).
- Native `license` gate for Maven, Gradle, Node, Python and Swift projects: it checks the licenses of the resolved dependencies against a deny or allow policy in `[checks.license]` (`deny`, `allow`, `exceptions`, `scopes`, `unknown`), reading only local metadata. `ward sbom` writes a CycloneDX 1.6 JSON bill of materials and `ward licenses` the third-party license notice (#520).
- `ward licenses` bundles the full text of the Eclipse Public License 2.0, so a notice with EPL-2.0 dependencies carries the license itself. Ward's own `THIRD-PARTY-LICENSES` now includes that text for the Eclipse JDT libraries it ships.
- The `build-policy` check (`uses = "build-policy"`) runs the native `ward:build/*` rules over the build files of Maven, Gradle, Node and Python projects without starting a process: `plugin-version-pinned`, `no-dynamic-version`, `minimum-build-tool-version`, `duplicate-dependency-declaration`, `dependency-convergence` and `require-upper-bound`. The last two read the versions that Maven requested before it chose one, with the defaults of Maven Enforcer's `dependencyConvergence` and `requireUpperBoundDeps` (every scope, the Maven version order), and `EnforcerParitySmokeTest` runs the real plugin over their fixtures. `ward init --preset strict` selects both for Maven projects. See [Build policy rules](website/src/content/docs/reference/build-policy-rules.mdx) (#521).
- Native OpenAPI breaking-change rules `ward:openapi/<check id>`, one for each error-level check of oasdiff 1.37.0 that Ward can evaluate (346 of 359; the others are listed as not applicable in the rule catalog with their reason). Each has a base and revision fixture whose result a real oasdiff run confirmed; the opt-in `OasdiffParitySmokeTest` and the `oasdiff-parity` CI job keep them in step (#523).
- Ward's own security rules, `ward:<language>/<weakness>` in the category `security`, so a project needs no external source scanner: 66 rules written from the MITRE CWE and the JDK, Node.js, Python and framework documentation. Java has 13 taint rules (`cross-site-scripting`, `untrusted-redirect`, `server-side-request-forgery`, `ldap-injection`, `xpath-injection`, `log-forging`, `header-splitting`, `untrusted-deserialization`, `script-injection`, `expression-injection`, `jndi-injection`, `unsafe-reflection`, `regex-injection`) and 17 declarative rules (weak hash, cipher, ECB mode, RSA padding and key size, insecure random, hostname and certificate checks, TLS protocol, clear-text transport, JWT verification, hard-coded password, XML entities, deserialization settings, CSRF, CORS, cookie flags). JavaScript and TypeScript have 10 taint rules and 18 declarative rules, and Python has 8 declarative rules. Rules that report injection, broken cryptography, certificate bypass, unsafe deserialization or hard-coded credentials are `error` in `standard` and `strict`; hardening rules are `warning` in `strict` only. Taint is intra-procedural, as for the existing injection rules. Every rule has a catalog row with its CWE ids and sources; see [security rules](https://github.com/RicardoRB/ward-ai/blob/main/website/src/content/docs/reference/security-rules.mdx).
- The existing `sql-injection`, `command-injection` and `path-injection` rules of Java, and `command-injection` of JavaScript and TypeScript, gain sinks: Hibernate `Session` and jOOQ `DSLContext` plain SQL, Commons Exec `CommandLine`, `PrintWriter`, `PrintStream` and `ZipFile` file names, `shelljs` and `execa`; and `sql-injection` is a new rule of JavaScript and TypeScript.
- `api-compat` is a native check for Java and Kotlin. It compares the public API of the main sources in the working tree with the same sources at a baseline Git revision, `base` or the latest release tag, and reports incompatible changes as `api-compat:<kind>` findings (#526). It runs no build, no external tool and no network access; `[checks.api-compat]` takes `base` and `include`. Init offers it to Java and Kotlin projects with a release tag and the generated CI workflow fetches full history and tags. Kotlin visibility (`internal`, `private`), modality (closed by default), `object`, `data class`, properties as accessors, top-level declarations, nullability and default arguments are part of the model; `nullability-narrowed` and `default-argument-removed` are new kinds. The documented limits of the Kotlin reader are in the gates reference.
- The taint rules follow calls between the functions of one file, to a depth of four calls (a function that returns a source value is a source for its callers, and a function whose parameter reaches a sink is a sink for that argument), and Java types imported with a wildcard (`java.io.*`, `java.sql.*`, `javax.servlet.http.*` and the other JDK and servlet packages Ward lists) resolve to their receiver types. The Java sources now include the environment, system properties, standard input, sockets, database results and files. On the Juliet Java 1.3 suite the injection rules find about 22% to 34% of the cases that were all missed before (see `benchmark/security-report.txt`); flows across classes, through fields and through the response writer are still missed. A taint specification qualifier that starts with `*` matches the end of the qualifier text.
- Security rules for the groups the Semgrep removal left uncovered. Java: `ward:java/identity-cipher` (`NullCipher`), `static-iv`, `constant-gcm-nonce`, `unauthenticated-cbc` (CBC with padding), `environment-injection` and `session-attribute-injection`. JavaScript and TypeScript (Express and Node `crypto`): `cookie-missing-secure`, `cookie-missing-http-only`, `cookie-missing-same-site`, `cookie-broad-scope`, `missing-frame-protection`, `unfinished-aead`, `weak-gcm-tag-length` and `weak-argon2-parameters`. Not provided: a cookie-expiry rule (a cookie with no expiry is a browser-session cookie, and the matching CWE is deprecated), a reused-IV rule that follows a constant held in a field, and AWS CDK rules (Infrastructure as Code, out of scope).

### Changed

- **Breaking (alpha):** The `enforcer` gate is removed. `ward init` no longer offers it and writes no `maven-enforcer-plugin` entry, no `config/enforcer-rules.xml` and no Gradle equivalent (on Gradle the gate ran `gradle check`); `--gate enforcer` and `uses = "enforcer"` are rejected as an unknown gate, the `compliance` legacy identifier is no longer read, and a check whose command runs `enforcer:enforce` is a plain command check. Re-running init removes a `config/enforcer-rules.xml` that carries Ward's marker and keeps and reports one you edited; the plugin entry in `pom.xml` is yours to remove. Migration: replace `[checks.enforcer]` with `build-policy` and select `build.dependency-convergence` and `build.require-upper-bound` in `ward-rules.toml` (`ward init --preset strict` does it for Maven); see [moving from the `enforcer` gate](website/src/content/docs/reference/build-policy-rules.mdx).
- **Breaking (alpha, no migration):** the `semgrep` gate type is removed, the way `gitleaks` is. A `ward.toml` with `uses = "semgrep"` is a configuration error (exit `2`) that lists the supported types, and `ward init --gate semgrep` fails as an unknown gate. A check whose `command` runs Semgrep is a plain `command` check: Ward reports its exit status, extracts no findings and gives no install guidance. Delete the `uses` key to keep running it that way. Also removed: the Semgrep step of the generated CI workflow, its partial-check table, its install-catalog entry, its doctor probe and its command type inference. Use the security rules of the `ward` gate instead.
- **Breaking (alpha, no migration):** Ward removes the 131 rules that were written from rows of the Semgrep registry (59 for Java, 72 for JavaScript and TypeScript), because the registry rules are under the Semgrep Rules License, which does not allow distributing them. Their rule files, fixtures, taint specifications, catalog rows, the pinned id list and the Semgrep parity tests are deleted, and the ids leave the `ward-rules.toml` schema and the rule reference. A `ward-rules.toml` that names a removed id is invalid: `[select]` reports `unknown rule` and a `[rules.<id>]` table reports `unknown rule id` (exit `2`); remove the id. The `standard` and `strict` presets lose all 131. A check whose command runs Semgrep still works as a plain command check. Detection that is lost, and the rule of Ward's own security rules that now covers part of it:
  - Java, weak or broken cryptography (MD5, SHA-1, SHA-224, DES, 3DES, RC2, RC4, Blowfish key size, default AES, null cipher, CBC padding oracle, GCM nonce reuse, static initialization vector): covered in part by `ward:java/weak-hash`, `weak-cipher`, `weak-key-size` and `ecb-mode`; no replacement for the null cipher, the CBC padding oracle, GCM nonce reuse and the static initialization vector.
  - Java, RSA without padding: `ward:java/rsa-without-padding`.
  - Java, unencrypted sockets, plain-HTTP requests, insecure SMTP and weak SSL contexts: `ward:java/cleartext-transport` and `weak-tls-protocol`, narrower for sockets and SMTP.
  - Java, JWT `none` algorithm, decode without verify and hard-coded secrets: `ward:java/jwt-without-verification` and `hard-coded-password`.
  - Java, unsafe Jackson and JMS deserialization: `ward:java/untrusted-deserialization` and `unsafe-deserialization-settings`.
  - Java, XML external entities (`DocumentBuilderFactory`, `SAXParserFactory`, `TransformerFactory`, `XMLInputFactory`): `ward:java/xml-external-entities`.
  - Java, SQL injection variants (JDBC, Spring, JBoss) and operating-system command, environment and file-path injection from request data: the existing `sql-injection`, `command-injection` and `path-injection` rules; no replacement for environment and session injection.
  - Java, reflected HTML and direct response writing: `ward:java/cross-site-scripting`, narrower.
  - Java, open redirect, response splitting, LDAP, XPath and log forging: `ward:java/untrusted-redirect`, `header-splitting`, `ldap-injection`, `xpath-injection` and `log-forging`; no replacement for URL host and session taint.
  - Java, MD5 as a password hash: `ward:java/weak-hash`.
  - JavaScript and TypeScript, code injection (`eval`, `vm`, `vm2`, sandbox runners, Puppeteer, Phantom, Chrome remote interface, `child_process`, Deno `run`, string-built code): `ward:javascript/script-injection` and `shell-option`; the existing `command-injection`.
  - JavaScript and TypeScript, SQL injection for Knex, MySQL, `pg` and Sequelize and DynamoDB filters: `ward:javascript/sql-injection` and `nosql-injection` (and the `typescript` ids of the same names), without the library-specific sinks Ward cannot confirm; DynamoDB filters are covered in part by `nosql-injection`.
  - JavaScript and TypeScript, XML external entities (expat, xml2json, libxml): `ward:javascript/xml-external-entities`.
  - JavaScript and TypeScript, unsafe deserialization: `ward:javascript/untrusted-deserialization`.
  - JavaScript and TypeScript, HTML and XSS sinks (`res.send`, raw HTML, template rendering, React unsanitized properties and methods, `dangerouslySetInnerHTML` variants, Angular trust bypasses and disabled SCE): `ward:javascript/dom-xss`, `template-injection` and `unsafe-framework-trust`, narrower; the existing `xss` and `no-dangerously-set-inner-html` stay.
  - JavaScript and TypeScript, open redirects and SSRF: `ward:javascript/untrusted-redirect` and `server-side-request-forgery`.
  - JavaScript and TypeScript, path traversal for `sendFile` and `path.join`: the existing `path-traversal`, narrower; no replacement for directory listing.
  - JavaScript and TypeScript, Express cookie and session settings: `ward:javascript/insecure-cookie`; no replacement for cookie name, domain, path and expiry checks.
  - JavaScript and TypeScript, JWT checks and hard-coded secrets (JWT, session, Passport): `ward:javascript/jwt-without-verification` and `hard-coded-secret`.
  - JavaScript and TypeScript, CORS misconfiguration and wildcard `postMessage`: `ward:javascript/permissive-cors` and `wildcard-post-message`; no replacement for `X-Frame-Options`.
  - JavaScript and TypeScript, TLS verification bypass and plain-HTTP requests: `ward:javascript/tls-verification-disabled` and `cleartext-transport`.
  - JavaScript and TypeScript, cipher IV and tag settings: `ward:javascript/static-iv`; no replacement for the missing `final` of an AEAD cipher or the GCM tag length.
  - JavaScript and TypeScript, unsafe Argon2 configuration, AWS CDK bucket, queue and CodeBuild settings, Intercom user hash: no replacement.
  - Removed Java ids: `blowfish-insufficient-key-size`, `cbc-padding-oracle`, `crlf-injection-logs`, `des-is-deprecated`, `desede-is-deprecated`, `documentbuilderfactory-disallow-doctype-decl-false`, `documentbuilderfactory-disallow-doctype-decl-missing`, `documentbuilderfactory-external-general-entities-true`, `documentbuilderfactory-external-parameter-entities-true`, `ecb-cipher`, `find-sql-string-concatenation`, `formatted-sql-string`, `gcm-nonce-reuse`, `http-response-splitting`, `httpget-http-request`, `httpservlet-path-traversal`, `insecure-jms-deserialization`, `insecure-smtp-connection`, `jackson-unsafe-deserialization`, `java-jwt-decode-without-verify`, `java-jwt-hardcoded-secret`, `java-jwt-none-alg`, `jax-rs-path-traversal`, `md5-used-as-password`, `no-direct-response-writer`, `no-null-cipher`, `no-static-initialization-vector`, `rsa-no-padding`, `saxparserfactory-disallow-doctype-decl-missing`, `servletresponse-writer-xss`, `spring-sqli`, `spring-unvalidated-redirect`, `tainted-cmd-from-http-request`, `tainted-env-from-http-request`, `tainted-file-path`, `tainted-html-string`, `tainted-ldapi-from-http-request`, `tainted-session-from-http-request`, `tainted-sql-from-http-request`, `tainted-sql-string`, `tainted-sqli`, `tainted-system-command`, `tainted-url-host`, `tainted-xpath-from-http-request`, `transformerfactory-dtds-not-disabled`, `unencrypted-socket`, `unvalidated-redirect`, `use-of-aes-ecb`, `use-of-blowfish`, `use-of-default-aes`, `use-of-md5`, `use-of-md5-digest-utils`, `use-of-rc2`, `use-of-rc4`, `use-of-sha1`, `use-of-sha224`, `use-of-weak-rsa-key`, `weak-ssl-context`, `xmlinputfactory-possible-xxe`.
  - Removed JavaScript and TypeScript ids: `aead-no-final`, `angular-bypasssecuritytrust`, `aws-cdk-bucket-enforcessl`, `awscdk-bucket-encryption`, `awscdk-bucket-grantpublicaccessmethod`, `awscdk-codebuild-project-public`, `awscdk-sqs-unencryptedqueue`, `bypass-tls-verification`, `chrome-remote-interface-compilescript-injection`, `code-string-concat`, `cors-misconfiguration`, `create-de-cipher-no-iv`, `deno-dangerous-run`, `detect-angular-element-taint`, `detect-angular-sce-disabled`, `detect-angular-trust-as-method`, `detect-child-process`, `detect-eval-with-expression`, `direct-response-write`, `dynamodb-request-object`, `express-check-directory-listing`, `express-cookie-session-default-name`, `express-cookie-session-no-domain`, `express-cookie-session-no-expires`, `express-cookie-session-no-httponly`, `express-cookie-session-no-path`, `express-cookie-session-no-secure`, `express-expat-xxe`, `express-insecure-template-usage`, `express-jwt-hardcoded-secret`, `express-jwt-not-revoked`, `express-libxml-noent`, `express-open-redirect`, `express-path-join-resolve-traversal`, `express-phantom-injection`, `express-puppeteer-injection`, `express-res-sendfile`, `express-sandbox-code-injection`, `express-sequelize-injection`, `express-session-hardcoded-secret`, `express-ssrf`, `express-third-party-object-deserialization`, `express-vm-injection`, `express-vm2-injection`, `express-xml2json-xxe`, `express-xml2json-xxe-event`, `gcm-no-tag-length`, `hardcoded-jwt-secret`, `hardcoded-passport-secret`, `intercom-settings-user-identifier-without-user-hash`, `js-open-redirect`, `jwt-none-alg`, `jwt-simple-noverify`, `knex-sqli`, `mysql-sqli`, `node-knex-sqli`, `pg-sqli`, `raw-html-concat`, `raw-html-format`, `react-dangerouslysetinnerhtml`, `react-insecure-request`, `react-unsanitized-method`, `react-unsanitized-property`, `require-request`, `res-render-injection`, `sequelize-sqli`, `tainted-html-response`, `tainted-html-string`, `unsafe-argon2-config`, `vm-runincontext-injection`, `wildcard-postmessage-configuration`, `x-frame-options-misconfiguration`.
- **Breaking (alpha):** `openapi-lint` runs these rules and no longer runs the vacuum binary. It takes no `command`: a `command` on it is a configuration error (exit `2`), so remove it from `ward.toml`. `vacuum.conf.yaml` is no longer read or refused, and init, the doctor, the install catalog and the generated CI workflow no longer mention vacuum. A Swagger 2.0 or OpenAPI 3.2 document, a syntax error or an unreadable `$ref` now ends the gate with an execution error.
- **Breaking:** the `gitleaks` gate is removed, with no migration flag. `uses = "gitleaks"` is rejected, a check that runs a `gitleaks` command is a plain `command` check with no findings, and `ward init --gate gitleaks` is an unknown gate: use `--gate secrets`. Init no longer offers or installs Gitleaks, `ward doctor` no longer probes for it, and the generated CI workflow no longer downloads it. The native check scans the working tree; the history is scanned only with `scan-history = true`.
- The coverage minimum lives in `ward.toml` and Ward enforces it for Maven, Gradle, Node and Python. A `coverage` check takes `minimum` (lines, required), `minimum-branches` and `report`, reads the JaCoCo XML or lcov report itself and fails below either minimum; it runs its `command` when it has one and only reads the report when it has none (Node, after the test gate). Branch coverage is read from JaCoCo XML and lcov. A missing, unparseable, truncated, stale or branchless report is an execution error, never a pass. Init writes no minimum into `pom.xml`, the Gradle build, a Vitest or Jest config, `bunfig.toml` or `.coveragerc`, and removes the Ward-written `jest.config.mjs` and `bunfig.toml` of earlier versions (with the marker) and rewrites a Ward-written `vitest.config.mjs` without its threshold. Node presets gain a `coverage` check after the test gate (not for Angular `ng-test`); Python's `coverage` command is now `coverage lcov -o .ward/reports/coverage.lcov`.
- **Breaking (alpha, no migration):** a `coverage` check without `minimum` is a configuration error, and a command that only contains `jacoco:check`, `jacocoTestCoverageVerification` or `coverage report` is a plain `command` check, no longer `coverage`; the generated commands are `jacoco:report`, `jacocoTestReport` and `coverage lcov`.
- **Breaking (alpha, no migration):** Python `strict` measures 85% of lines and 75% of branches separately, not coverage.py's combined 85% total, so a project at 85% combined can fail the branch minimum.
- **Breaking (alpha, no migration):** a multi-module Maven or Gradle build is checked as one total over every module's report, not per module.
- **Breaking (alpha, no migration):** the `trivy`, `dependency-check` and `pip-audit` gates are removed, with their init offers, the Trivy install and cache steps of the generated CI workflow, the `dependency-check` plugins of the Maven and Gradle tool configuration, the doctor checks of `NVD_API_KEY` and OSS Index, and their pages in the documentation. A check that runs one of those tools is now a plain `command` check; `uses = "trivy"`, `"dependency-check"` or `"pip-audit"` is a configuration error. Use `vulnerabilities` instead (#519).
- **Breaking (alpha, no migration):** the `license` gate no longer runs a build plugin. A `license` check that sets `command` is a configuration error, a license Ward cannot find or recognize now fails the gate (`unknown = "warn"` only warns), and init no longer writes `license-maven-plugin`, `com.github.jk1.dependency-license-report`, `licenseMerges` or `licenseReport` (#520).
- **Breaking (alpha, no migration):** `openapi-diff` and `openapi-compat` compare the spec with Ward's own rules instead of running `oasdiff`. A `command` on either gate is now a configuration error (exit `2`); remove it. Init no longer offers or installs oasdiff, `ward doctor` no longer looks for it, and `.oasdiff.*` files and `OASDIFF_CONFIG` are ignored. A spec, reference or Git read failure at either side of the comparison is an execution error (#523).

### Removed

- **Breaking (alpha):** `ward init` no longer adds the japicmp Maven plugin or the `me.champeau.gradle.japicmp` Gradle plugin, and `api-compat` no longer depends on `<distributionManagement>` or a published artifact: it compares sources with the latest release tag for Java and Kotlin (#526). Existing build files that already declare japicmp are left alone.
- **Breaking (alpha, no migration):** The OpenSpec integration. Ward no longer ships the `openspec`, `openspec-archived` and `openspec-junit-traceability` check types, the `root` setting of a check, the `ward spec` commands (`validate`, `guard`, `enforce`), `--exclude-spec` of `ward check` and `ward hook claude-code`, `--tool` and `--openspec-validation` of `ward init`, the OpenSpec installer and its step in the generated CI workflow, the OpenSpec checks of `ward doctor`, and the approved-artifact guard of every agent integration with its `PreToolUse` registrations. A `ward.toml` that still names a removed check type is rejected with the unknown-type error that lists the valid types. `ward integrate <agent>` and `--remove` delete the Ward-owned guard files that earlier versions wrote. To keep approved files from agent edits, use the agent's own permission rules or repository protection such as CODEOWNERS.

## 0.7.1 - 2026-11-12 (withdrawn)

This release was withdrawn on 2026-11-15. It shipped rules written from rows of the Semgrep registry, whose license does not allow distribution; its binaries and its tag are removed. Ward 0.8.0 removed those rules.

### Fixed

- A `break` or `continue` outside a loop, or a jump to a label that does not exist, no longer aborts the `ward` gate with `unexpected error`: the jump leaves the body like a `return`, and the other files and rules still run (#530).

## 0.7.0 - 2026-11-11 (withdrawn)

This release was withdrawn on 2026-11-15. It shipped rules written from rows of the Semgrep registry, whose license does not allow distribution; its binaries and its tag are removed. Ward 0.8.0 removed those rules.

### Added

- Five native Java rules that this repository's Checkstyle and PMD configuration enforces and Ward lacked: `ward:java/simplify-boolean-expression` (`x == true`, `x && true`, `c ? true : false`), `ward:java/unnecessary-parentheses` (precedence-clarifying parentheses are not reported), `ward:java/unused-private-field`, `ward:java/final-parameters` (a parameter the body uses and never assigns) and `ward:java/avoid-no-argument-super-constructor-call`. `ward:java/unnecessary-constructor` is now its own rule (#491).
- Thirteen native Python rules from the optional Pylint extensions, in the `strict` preset: `while-used`, `bad-builtin`, `bad-dunder-name`, `consider-math-not-float`, `consider-using-tuple`, `prefer-typing-namedtuple`, `consider-refactoring-into-while-condition`, `consider-ternary-expression`, `too-many-try-statements` (option `max-try-statements`, default 1), `overlapping-except`, `consider-using-assignment-expr`, `confusing-consecutive-elif` and `eq-without-hash`. Fourteen more Pylint checks are covered by existing rules (#336).
- Option `min-length` for the repeated string literal rules. `ward:java/multiple-string-literals` sets it to 3, so literals shorter than three characters are ignored, as PMD's `AvoidDuplicateLiterals` does; the default 0 keeps the other languages unchanged (#491).

### Changed

- **Breaking (alpha, no migration):** Rule categories are named after what a rule checks, not after the tool it came from. The 46 categories become 16: `accessibility`, `angular`, `architecture`, `complexity`, `concurrency`, `correctness`, `dead-code`, `docs`, `duplication`, `error-handling`, `experimental`, `naming`, `null-safety`, `performance`, `security` and `style`. For example `errorprone`, `bad-practice`, `problem` and `pylint` are now `correctness`; `sizes`, `metrics` and `design` are `complexity`; `javadoc` is `docs`; `mt-correctness` and `multithreading` are `concurrency`; `nullness` is `null-safety`; `a11y` is `accessibility`. Rule ids do not change. Update `categories` in `ward-rules.toml` and `ward rules list --category`; an old name is reported as an unknown category with the list of valid ones.
- The `ward` gate parses each source unit once per run. Type facts for Java, Node and Swift share the gate's parser, and the parse cache is bounded by source size instead of 64 entries. Measured on a 98-file TypeScript project: 457 parses become 98 and parse time drops from 209 s to 69 s, with an identical report (#502).
- The `ward` gate needs much less memory on large Java and Kotlin projects: the members of a class that every file shares are read once, not once per typed expression. On this repository the peak went from 9.0 GB to 3.3 GB with an identical report (#493). The members of a named TypeScript type are cached per file in the same way (#494).

### Fixed

- The `ward` gate no longer crashes with a `NullPointerException` on a JavaScript class that declares a `get` or `set` accessor. A getter is now a property of unknown type for the type-aware rules, and a setter declares nothing, as for TypeScript classes.
- Eleven Java rules reported code that the tool they mirror accepts. They now follow that tool's documented behaviour (#491):
  - `ward:java/avoid-instantiating-objects-in-loops` follows PMD's exemptions, such as `throw new` inside a loop.
  - `ward:java/avoid-literals-in-if-condition` reports only literals that are direct operands of the condition, and ignores `-1` and `0` by default.
  - `ward:java/unused-private-method` counts method references such as `Type::method`, and PMD's exemptions.
  - `ward:java/immutable-field` reports a field only when every write is in its initializer or a constructor, and leaves injected annotated fields alone.
  - `ward:java/magic-number` waives literals that initialize a `final` variable, as Checkstyle does.
  - `ward:java/public-member-in-non-public-type` follows the effective visibility of the member: interface members, `@Override` methods and `main` are not reported, and the finding is on the `public` modifier.
  - `ward:java/signature-declare-throws-exception` reports only an exact `Exception`, and not tests or overriding methods.
  - `ward:java/avoid-deeply-nested-if-stmts` no longer counts each `else if` of a chain as another nesting level; only a plain `else` block adds one, as in PMD.
  - `ward:java/confusing-argument-to-varargs-method` no longer reports a generic varargs method, whose erased parameter hides the inferred element type, or an array passed for a varargs type that an array cannot be an element of, such as `OpenOption...`.
  - `ward:java/declaration-order` now stands only for Checkstyle's `DeclarationOrder`; PMD's `FieldDeclarationsShouldBeAtStartOfClass`, which the same rule used to mirror, is the new rule `ward:java/field-declarations-should-be-at-start-of-class` (a field after a method, constructor or nested type; initializer blocks and nested enums may come first).
  - `ward:java/string-instantiation` reports only `new String(value)` with a `String` argument, as PMD does, and no longer a `String` built from bytes or characters.

## 0.6.0 - 2026-11-10 (withdrawn)

This release was withdrawn on 2026-11-15. It shipped rules written from rows of the Semgrep registry, whose license does not allow distribution; its binaries and its tag are removed. Ward 0.8.0 removed those rules.

### Added

- Native TypeScript and JavaScript type resolution for the type-aware rules, with no `node` or TypeScript compiler process: types come from the project sources, the `.d.ts` files of the project and of the packages in `node_modules` (`types`, `typings`, `@types/*`) and the library of the installed `typescript` package, and what needs full checking is unknown. Package indexes are kept under `.ward/cache/types/node/`, named by the SHA-256 of the package. The TypeScript grammar now accepts ambient declarations (`declare function` and `declare class`, `declare global`, accessors and index signatures in declarations, labeled tuple elements and assertion signatures).
- Native Java rules `ward:java/no-field-di` and `ward:java/no-standard-streams` in the `strict` preset: a field annotated with `@Inject`, `@Autowired` or `@Resource` is reported, and so is any access to `System.out`, `System.err` or `System.in`. They replace the two checks that only the generated ArchUnit test enforced (#374).
- `ward-rules.toml`, an optional file that selects the rules of the `ward` gate (`[select]`: preset, languages, categories, include, exclude) and tunes them per rule (`max`, name patterns, `severity`, `exclude-paths`), validated with every problem reported by file, table and key and checked by `ward doctor`. `rules-file` in `[checks.ward]` points to another file. A `rules` list in `ward.toml` together with the file is an error. There are no suppressions of any kind (#280).
- `ward-frontend-antlr`, an internal analysis module with ANTLR parsers for Java, Kotlin, TypeScript, JavaScript, Python and Swift. Parsers are generated at build time from grammars pinned by commit, and syntax errors are returned as diagnostics next to a partial tree. No CLI behavior changes.
- `ward format [--check] [--format text|json] [<path>...]`, Ward's native formatter for the sources of Java, Kotlin, TypeScript, JavaScript, Python and Swift, in Ward style and with no external formatter. Each file is checked for syntax, tokens, syntax-tree shape and idempotence before it is written. It writes atomically and keeps file permissions; `--check` writes nothing and exits 1 when a file needs formatting. Settings come from `.editorconfig`. Symbolic links are reported and never rewritten.
- The `ward-format` quality gate (`uses = "ward-format"`, phase FAST), which reports one error per file that `ward format` would change, at its first differing line. Refused files and invalid `.editorconfig` settings are execution errors, never a pass. It supports partial runs and caches results by content and settings.
- `ward:architecture/cycles` and `ward:architecture/independence`, the slice cycle and feature independence rules of the `ward` gate, configured by `[checks.ward.cycles]` and `[[checks.ward.independence]]`. One meaning for Java, Python, TypeScript, JavaScript and Swift: a cycle is reported between two or more slices, the first directory under a source root (#246).

### Changed

- **Breaking (alpha, no migration):** Inside `ward-rules.toml` a rule is named `<language>.<rule-name>` only: `[rules.java.hidden-field]` for its table, and `"java.star-import"` in `[select]` `include` and `exclude`. A table named by the quoted rule id (`[rules."ward:java/hidden-field"]`) or a list entry written as the id (`"ward:java/star-import"`) is a configuration error that names the form to write. `ward init --preset strict` writes `[rules.java.hidden-field]`, `ward rules enable` and `disable` still take the printed id and write the dotted name, and the JSON schema lists only the dotted names. Rule ids are unchanged wherever Ward prints them (findings, JSON, SARIF, `ward rules`) and in the `rules` list of `ward.toml`.
- **Breaking (alpha, no migration):** `ward init` on a TypeScript or JavaScript project (npm, pnpm, Yarn and Bun, `standard` and `strict`, Angular, Turborepo members included) no longer generates ESLint (with typescript-eslint and angular-eslint), Biome's lint or Semgrep's `p/javascript` and `p/typescript` packs: no gate, no config file, no package entry, no script, no partial-run table, and no agent rule for them. It writes `ward-rules.toml` with the preset and the project's languages (`"typescript"`, `"javascript"`), so the `ward` gate applies Ward's rules instead. Ward's rules replace the standard rows of those tools; the Node compatibility matrix expects their findings from the `ward` gate (#343).
- **Breaking (alpha, no migration):** The `swiftlint` gate type is removed. A check with `uses = "swiftlint"` is rejected as an unknown gate type. A `swiftlint lint` command is a plain `command` check, with no structured findings and no learning. Init still removes the Ward-owned SwiftLint files that an earlier init wrote. Ward's own Swift rules (`ward:swift/*`) are unchanged.
- **Breaking (alpha, no migration):** `ward init` on a Swift project (SwiftPM and Xcode, `standard` and `strict`) no longer generates SwiftLint: no `swiftlint` gate, no `.swiftlint.yml`, no `.ward/swiftlint-strict.yml`, no `.ward/swift-tools/` package, no resolve or CI cache step, and no `.ward/swift-tools/.build/` ignore entry. The `ward` check applies Ward's Swift rules instead, and init writes `ward-rules.toml` with `languages = ["java", "swift"]`. Re-running init removes the Ward-owned SwiftLint files of an earlier init and keeps a `.swiftlint.yml` you wrote. Strict Swift runs no `--strict` SwiftLint (#345, #457).
- **Breaking (alpha, no migration):** `ward init` on a Java project (Maven and Gradle, `standard` and `strict`) no longer generates Checkstyle, PMD or SpotBugs: no gate, no rule file under `config/`, no build plugin or block, no partial-run table, and the agent rules no longer quote their limits. The `ward` check applies Ward's Java rules instead, and init writes an ownership-marked `ward-rules.toml` with `[select] preset = "<preset>"` and `languages = ["java"]`. The file is written for every project, so the Ward rules of Kotlin, TypeScript, JavaScript, Python and Swift stay off, and each of those keeps its external tools until its own migration lands (#342, #460). A Kotlin project still gets the `checkstyle`, `pmd` and `spotbugs` gates.
- **Breaking (alpha, no migration):** Ward no longer has a `ruff` gate type. A check with `uses = "ruff"` is rejected as an unknown gate type, and a `ruff check` command is a plain `command` check with no findings. Ruff, Pylint and vulture are removed from the docs as Ward gates; Ward's Python rules replace their lint checks.
- **Breaking (alpha, no migration):** `ward init` on a Python project (uv, Poetry or pip, `standard` and `strict`) no longer offers, selects, pins, configures or documents Ruff: the `ruff` gate is gone from both presets, and init writes no `ruff.toml`, no `ruff` package pin, no Ruff partial-run table and no Ruff agent rule. The `ward-rules.toml` that init writes lists `languages = ["java", "python"]`, so the `ward` check applies Ward's Python rules of the preset. A project's own `ruff.toml` is left unchanged. The `ruff` gate type stays until #452 (#344, #456).
- **Breaking (alpha, no migration):** Ward checks architecture with its own `ward:architecture/*` rules only. `ward init` always writes the `ward` layer, cycle and independence tables (`[[checks.ward.layers]]`, `[checks.ward.cycles]`, `[[checks.ward.independence]]`), and `--architecture-engine` is gone, with no `tool` engine or golden. Init no longer generates an ArchUnit test, a dependency-cruiser config, an Import Linter config or Harmonize rules, and no longer offers the `archunit`, `dependency-cruiser`, `import-linter` or `harmonize` gates; `uses = "archunit"`, `"dependency-cruiser"`, `"import-linter"` or `"harmonize"` is rejected. A `command` check that runs one of those tools still works as a custom check. The strict preset's ArchUnit coding rules are the native `ward:java/no-field-di` and `ward:java/no-standard-streams` rules (#374).
- **Breaking (alpha, no migration):** Ward formats with `ward-format` alone. `ward init` always selects the `ward-format` gate and writes an ownership-marked `.editorconfig` with Ward's defaults when the project has none. The external formatters are gone: the Spotless `format` gate (Maven and Gradle), `prettier`, `ruff-format` and `swift-format`, with their init generation, plugins, packages, config files, finding parsers and partial-run tables. Biome stays as the linter with its formatter off. `ward init` has no `--format-engine` option, and no `tool` engine or golden remains. A `ward.toml` that names one of the removed gates (`uses = "format"`, `"prettier"`, `"ruff-format"` or `"swift-format"`) is rejected with the list of supported types; a `command` check that runs the tool still works as a custom check.
- Ward's Kotlin rules replace detekt and ktlint. Ward never ran either tool, so nothing changes at run time; the docs no longer present them as tools Ward measures against, and a contract test keeps both out of the gates, finding sources, plugins and main classes (#450).
- `ward init --preset strict` writes the cycle and independence tables instead of ArchUnit's cycle rule, dependency-cruiser's `no-circular` and Import Linter's independence contract. Inside one slice, file cycles are not reported (#246, #374).
- The analysis API's `LanguageFrontend.parse` returns a `ParseResult` (syntax tree plus diagnostics) instead of a bare `SyntaxNode`. The analysis API is unreleased, so no released behavior changes.
- The Maven build is a parent project (`ward-parent`) with a `ward-cli` module. Build outputs moved to `ward-cli/target/` (`ward.jar`, `ward`, `ward.exe`); the released binaries and jar keep their names.

### Removed

- **BREAKING:** No macOS Intel (x86_64) binary is published, and `install.sh` stops on an Intel Mac with a message. Native builds use GraalVM Community 25.5, which has no macOS x64 release. On an Intel Mac, build `ward.jar` from source and run it on JDK 25.
- **BREAKING (alpha, no migration):** Ward no longer has the `eslint` and `biome` gate types, their finding sources, or the Semgrep `p/javascript` and `p/typescript` packs. A `ward.toml` that names `uses = "eslint"` or `uses = "biome"` is rejected with the list of supported types, and a `command` check that runs `eslint` or `biome` is a plain command check with no structured findings and no learning. The Semgrep gate stays for Python (#451, #455).
- **BREAKING:** Ward no longer wires Checkstyle, PMD, SpotBugs, Error Prone, NullAway or Semgrep's `p/java` pack. Their gates, finding sources, Maven and Gradle plugin declarations, preset rule files and init defaults are gone. Java and Kotlin projects use the `ward` gate and `ward-rules.toml`; Semgrep stays for TypeScript, JavaScript and Python, each with its own pack.

### Fixed

- Learning (`ward check --learn-after`) tracks the rule findings of the `ward` gate again, as documented. Since the external Java tools were removed it recorded nothing for Java, Kotlin and Swift projects.
- `ward init` on a Maven aggregator without its own sources places the architecture test in the module that owns them.

## [0.5.0] - 2026-11-02

### Added

- Swift support for SwiftPM and Xcode projects, with Swift quality gates and an Xcode version check.
- Android project detection and Android quality gates.
- Java quality gates placed in each module of multi-module builds, and in Kotlin module scripts for Gradle.

### Changed

- Feature code is split by toolchain across the application and infrastructure layers.

## [0.4.1] - 2026-10-31

### Added

- OpenAPI gate recommendations from the detected framework, with pinned tools installed by `init`.
- Turborepo support for other-language members, configured and checked by Ward.

### Changed

- Documentation positions Ward as the quality gate for AI coding agents.

## [0.4.0] - 2026-10-31

### Added

- Python support for `init`, gates, and frameworks, including Python detection and package managers.
- Turborepo workspaces: planning, member scripts, and `turbo.json` tasks written through `ward init`.

### Changed

- `ward.toml` moves to configuration version 2.

## [0.3.0] - 2026-10-29

### Added

- Free-named checks, tool types, and an edit profile.

### Changed

- **Breaking:** the configuration changes to free-named checks and tool types. Projects on the old
  format must migrate their `ward.toml`.

## [0.2.1] - 2026-10-29

### Added

- The `ward-fix` agent skill, which fixes failing gates in the code.
- Framework labels for Maven and Gradle projects, such as Spring Boot, Quarkus, Micronaut, and Helidon.
- Angular 21 and 22 support, and Vue and Svelte matrix cells.
- Explanations for gates that cannot run in the current environment.

### Changed

- Documentation covers TypeScript and JavaScript support.

## [0.2.0] - 2026-10-27

### Added

- TypeScript and JavaScript support: detection, `init`, and Node quality gates (ESLint, `tsc`, Stryker).
- Presets for TypeScript and JavaScript, including a `strict` preset.
- Angular, Vue, Svelte, and Astro setup, with each framework's own type checker.
- Biome support, and dependency-cruiser architecture rules for Node projects.
- A Node compatibility matrix, and a warning for Node versions below 22.
- A Java compatibility matrix, and Java tool versions chosen for the build's JDK.
- Junie CLI integration.

## [0.1.3] - 2026-10-24

### Added

- Windows native executable verification before release.

### Changed

- Mutation testing runs only on changed classes in pull requests. Native checks run on Windows.

### Fixed

- The license and gitleaks gates now catch what they claim to.

## [0.1.2] - 2026-10-24

### Added

- OpenAPI contract gates with vacuum and oasdiff.
- A PowerShell installer and Git Bash support for Windows.
- `init` support for Java projects nested in monorepos.

### Changed

- The website is easier to scan, and CI actions move to Node 24.

## [0.1.1] - 2026-10-22

### Changed

- **Breaking:** the project is renamed from Sentinel to Ward. Commands, configuration, and binaries use `ward`.

## [0.1.0] - 2026-10-21

### Added

- Generated GitHub CI workflow and `AGENTS.md` gate rules from `init`.
- Git quality hooks with JSON output.
- SARIF upload to GitHub code scanning.
- A notice when a newer release is available.
- Learning from recurring rule findings instead of gate failures.

### Fixed

- Compact JSON and SARIF reports from `check`.
- Java 25 compatible PIT configuration from `init`.

## [0.0.11] - 2026-10-19

### Added

- `check` runs only the gates for edited files after each agent edit.

### Fixed

- Partial checks match edited paths reached through symlinks.

## [0.0.10] - 2026-10-18

### Added

- The Sentinel agent skills plugin, with `sentinel-init` and `sentinel-tune`.
- Severity filtering for `check` output.
- Improved gate setup in `init`.

### Fixed

- Files generated by `init` work on real Maven and Gradle projects.

## [0.0.9] - 2026-10-17

### Added

- An exact change plan that `init` shows before it writes, with `--dry-run`.
- Reconfigure pre-fill, availability checks, and progress in the init wizard.

### Fixed

- Optional init questions are asked on a terminal, and unticked preset gates are honored.
- Maven tools are placed correctly, and `pom.xml` is validated before writing.

## [0.0.8] - 2026-10-17

### Added

- Verification of all agent harness integrations, with repaired adapters.
- Native-image compatibility checks in pull requests and releases.

### Fixed

- The build version is reported from native images.

## [0.0.7] - 2026-10-16

### Fixed

- Finding JSON codecs are initialized at build time for the native image.

## [0.0.6] - 2026-10-16

### Added

- Structured findings and SARIF output for `check`.
- Multi-agent harness integrations and OpenCode V2 integration.
- Gradle build support.
- Project-aware quality gate version compatibility.
- `init` quality profiles and Git hooks.
- Apache-2.0 license, with notices shipped in releases.
- A CI provider integration guide.

### Fixed

- The command reference link in the docs.

## [0.0.5] - 2026-10-14

### Added

- OpenSpec validation loop for agent integrations, and the OpenSpec JUnit traceability gate.
- OpenSpec change provenance enforcement, and guards for approved OpenSpec artifacts.
- OpenSpec validation tool integration.

## [0.0.4] - 2026-10-10

### Added

- Invocation-scoped verbose diagnostics.

### Changed

- Comprehensive PMD categories are enabled, and style findings are fixed.

## [0.0.3] - 2026-10-10

### Fixed

- The release publish job.

## [0.0.2] - 2026-10-10

### Added

- A release install script.

### Changed

- The README is rewritten, and docs are checked against the CLI.

### Fixed

- The retired macOS runner is replaced.

## [0.0.1] - 2026-10-08

Initial release, published as `sentinel-ai`.

### Added

- Quality gates for Java projects: format, Checkstyle, PMD, SpotBugs, JaCoCo coverage, PIT mutation
  testing, ArchUnit, Semgrep, gitleaks, Trivy, Enforcer, license, and API compatibility.
- Quality presets, named profiles, and a live quality check report.
- Agent hooks and an OpenCode plugin that run quality checks after agent edits.
- `init` with interactive setup, and native binaries published in releases.
- A bounded quality loop and a typed gate registry.
- Starlight documentation site.

[0.11.3]: https://github.com/RicardoRB/ward-ai/compare/v0.11.2...v0.11.3
[0.11.2]: https://github.com/RicardoRB/ward-ai/compare/v0.11.1...v0.11.2
[0.11.1]: https://github.com/RicardoRB/ward-ai/compare/v0.11.0...v0.11.1
[0.11.0]: https://github.com/RicardoRB/ward-ai/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/RicardoRB/ward-ai/compare/v0.9.1...v0.10.0
[0.9.1]: https://github.com/RicardoRB/ward-ai/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/RicardoRB/ward-ai/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/RicardoRB/ward-ai/compare/v0.5.0...v0.8.0
[0.5.0]: https://github.com/RicardoRB/ward-ai/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/RicardoRB/ward-ai/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/RicardoRB/ward-ai/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/RicardoRB/ward-ai/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/RicardoRB/ward-ai/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/RicardoRB/ward-ai/compare/v0.1.3...v0.2.0
[0.1.3]: https://github.com/RicardoRB/ward-ai/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/RicardoRB/ward-ai/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/RicardoRB/ward-ai/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/RicardoRB/ward-ai/compare/v0.0.11...v0.1.0
[0.0.11]: https://github.com/RicardoRB/ward-ai/compare/v0.0.10...v0.0.11
[0.0.10]: https://github.com/RicardoRB/ward-ai/compare/v0.0.9...v0.0.10
[0.0.9]: https://github.com/RicardoRB/ward-ai/compare/v0.0.8...v0.0.9
[0.0.8]: https://github.com/RicardoRB/ward-ai/compare/v0.0.7...v0.0.8
[0.0.7]: https://github.com/RicardoRB/ward-ai/compare/v0.0.6...v0.0.7
[0.0.6]: https://github.com/RicardoRB/ward-ai/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/RicardoRB/ward-ai/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/RicardoRB/ward-ai/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/RicardoRB/ward-ai/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/RicardoRB/ward-ai/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/RicardoRB/ward-ai/releases/tag/v0.0.1
