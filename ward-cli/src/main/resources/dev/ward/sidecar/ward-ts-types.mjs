// Ward type sidecar for TypeScript and JavaScript.
//
// Run by node as: node ward-ts-types.mjs <projectRoot>
// It loads the project's own typescript package and tsconfig.json. Protocol, one JSON object per
// stdin line: a request {"path","text","spans":[[start,end],...]} gets the response
// {"types":{"start:end":"TypeName"}} on stdout. Spans with no answer are left out. Exit 1 when
// typescript cannot be resolved; exit 2 on a malformed request. Diagnostics go to stderr only.
import { createRequire } from 'node:module';
import { createInterface } from 'node:readline';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

const projectRoot = resolve(process.argv[2] ?? '.');
const EXIT_NO_TYPESCRIPT = 1;
const EXIT_MALFORMED = 2;

let ts;
try {
  ts = createRequire(join(projectRoot, 'package.json'))('typescript');
} catch {
  process.stderr.write('typescript is not resolvable from the project root\n');
  process.exit(EXIT_NO_TYPESCRIPT);
}

const configPath = ts.findConfigFile(projectRoot, ts.sys.fileExists);
let options = { allowJs: false, noEmit: true };
let fileNames = [];
if (configPath) {
  const config = ts.readConfigFile(configPath, ts.sys.readFile);
  const parsed = ts.parseJsonConfigFileContent(config.config ?? {}, ts.sys, dirname(configPath));
  options = { ...parsed.options, noEmit: true };
  fileNames = parsed.fileNames;
}

const texts = new Map();
const versions = new Map();
const service = ts.createLanguageService(
  {
    getScriptFileNames: () => [...new Set([...fileNames, ...texts.keys()])],
    getScriptVersion: (file) => String(versions.get(file) ?? 0),
    getScriptSnapshot: (file) => {
      const text = texts.has(file) ? texts.get(file) : existsSync(file) ? readFileSync(file, 'utf8') : undefined;
      return text === undefined ? undefined : ts.ScriptSnapshot.fromString(text);
    },
    getCurrentDirectory: () => projectRoot,
    getCompilationSettings: () => options,
    getDefaultLibFileName: (settings) => ts.getDefaultLibFilePath(settings),
    fileExists: ts.sys.fileExists,
    readFile: ts.sys.readFile,
    readDirectory: ts.sys.readDirectory,
    directoryExists: ts.sys.directoryExists,
    getDirectories: ts.sys.getDirectories,
  },
  ts.createDocumentRegistry(),
);

const JAVASCRIPT_FILE = /\.(c|m)?jsx?$/;

/** The innermost node that covers the whole span, so the smallest such node wins. */
function innermostNode(sourceFile, start, end) {
  let best;
  const visit = (node) => {
    const nodeStart = node.getStart(sourceFile);
    if (nodeStart <= start && node.end >= end) {
      if (!best || node.end - nodeStart <= best.end - best.start) {
        best = { node, start: nodeStart, end: node.end };
      }
      ts.forEachChild(node, visit);
    }
  };
  ts.forEachChild(sourceFile, visit);
  return best?.node;
}

function typesOf(request) {
  const types = {};
  if (!Array.isArray(request.spans) || request.spans.length === 0) {
    return types;
  }
  const file = resolve(projectRoot, request.path);
  if (JAVASCRIPT_FILE.test(file) && !options.allowJs) {
    return types;
  }
  texts.set(file, String(request.text ?? ''));
  versions.set(file, (versions.get(file) ?? 0) + 1);
  const program = service.getProgram();
  const sourceFile = program?.getSourceFile(file);
  if (!sourceFile) {
    return types;
  }
  const checker = program.getTypeChecker();
  const failures = service.getSemanticDiagnostics(file).filter((diagnostic) => diagnostic.start !== undefined);
  for (const [start, end] of request.spans) {
    const node = innermostNode(sourceFile, start, end);
    if (!node) {
      continue;
    }
    const type = checker.getTypeAtLocation(node);
    // An unresolved name is typed as any; it has no answer rather than the answer any.
    if (type.flags & ts.TypeFlags.Any && failures.some((d) => d.start < end && d.start + d.length > start)) {
      continue;
    }
    types[`${start}:${end}`] = checker.typeToString(type, undefined, ts.TypeFormatFlags.NoTruncation);
  }
  return types;
}

const lines = createInterface({ input: process.stdin, crlfDelay: Infinity });
lines.on('line', (line) => {
  let request;
  try {
    request = JSON.parse(line);
  } catch {
    process.stderr.write('malformed request\n');
    process.exit(EXIT_MALFORMED);
  }
  let types = {};
  try {
    types = typesOf(request);
  } catch {
    process.stderr.write('type analysis failed for one file\n');
  }
  process.stdout.write(`${JSON.stringify({ types })}\n`);
});
