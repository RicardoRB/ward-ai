// Ward type sidecar for TypeScript and JavaScript.
//
// Run by node as: node ward-ts-types.mjs <projectRoot>
// It loads the project's own typescript package and tsconfig.json. Protocol, one JSON object per
// stdin line: a request {"path","text","spans":[[start,end],...],"kinds":["type","shape","call",
// "annotations","overrides","throws","constant"]} gets the response {"facts":{"start:end":{"type":
// {"name":"number"},...}}} on stdout. Facts and spans with no answer are left out. Exit 1 when
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

/** A type reference: the printed name, its type arguments and its array depth. */
function typeRef(checker, type) {
  if (!type) {
    return undefined;
  }
  if (checker.isArrayType(type)) {
    const element = typeRef(checker, checker.getTypeArguments(type)[0]);
    return element && { ...element, array: (element.array ?? 0) + 1 };
  }
  const ref = { name: checker.typeToString(type, undefined, ts.TypeFormatFlags.NoTruncation) };
  if (type.flags & (ts.TypeFlags.NumberLike | ts.TypeFlags.StringLike | ts.TypeFlags.BooleanLike | ts.TypeFlags.Void)) {
    ref.primitive = true;
  }
  const args = type.flags & ts.TypeFlags.Object && type.objectFlags & ts.ObjectFlags.Reference ? checker.getTypeArguments(type).slice(0, type.target?.typeParameters?.length ?? 0) : [];
  const resolved = args.map((argument) => typeRef(checker, argument));
  if (resolved.length > 0 && resolved.every(Boolean)) {
    ref.args = resolved;
  }
  return ref;
}

function hasModifier(node, kind) {
  return (ts.getCombinedModifierFlags(node) & kind) !== 0;
}

function shapeOf(checker, declaration) {
  const kinds = [
    [ts.isClassDeclaration, 'class'],
    [ts.isInterfaceDeclaration, 'interface'],
    [ts.isEnumDeclaration, 'enum'],
  ];
  const found = kinds.find(([test]) => test(declaration));
  if (!found) {
    return undefined;
  }
  const supertypes = [];
  for (const clause of declaration.heritageClauses ?? []) {
    for (const base of clause.types) {
      const ref = typeRef(checker, checker.getTypeAtLocation(base));
      if (ref) {
        supertypes.push(ref);
      }
    }
  }
  return {
    kind: found[1],
    abstract: found[1] === 'interface' || hasModifier(declaration, ts.ModifierFlags.Abstract),
    final: false,
    sealed: false,
    supertypes,
  };
}

function callTarget(checker, signature) {
  const declaration = signature?.declaration;
  if (!declaration || !declaration.parent?.name) {
    return undefined;
  }
  const owner = checker.getTypeAtLocation(declaration.parent);
  const ownerRef = typeRef(checker, owner);
  const params = signature.getParameters().map((symbol) => typeRef(checker, checker.getTypeOfSymbolAtLocation(symbol, declaration)));
  if (!ownerRef || params.some((param) => !param)) {
    return undefined;
  }
  const returns = typeRef(checker, checker.getReturnTypeOfSignature(signature));
  return {
    declaring: { name: ownerRef.name },
    name: declaration.name ? declaration.name.getText() : '<init>',
    params,
    ...(returns ? { returns } : {}),
    static: hasModifier(declaration, ts.ModifierFlags.Static),
    varargs: signature.getParameters().some((symbol) => symbol.valueDeclaration && ts.isParameter(symbol.valueDeclaration) && symbol.valueDeclaration.dotDotDotToken),
  };
}

function overridesOf(checker, declaration) {
  const owner = declaration.parent;
  const overridden = [];
  if (!ts.isMethodDeclaration(declaration) || !(ts.isClassDeclaration(owner) || ts.isInterfaceDeclaration(owner))) {
    return overridden;
  }
  const name = declaration.name.getText();
  const queue = (owner.heritageClauses ?? []).flatMap((clause) => clause.types.map((base) => checker.getTypeAtLocation(base)));
  const seen = new Set();
  while (queue.length > 0) {
    const base = queue.shift();
    const symbol = base.getSymbol();
    if (!symbol || seen.has(symbol)) {
      continue;
    }
    seen.add(symbol);
    const method = base.getProperty(name);
    const signature = method?.valueDeclaration ? checker.getSignatureFromDeclaration(method.valueDeclaration) : undefined;
    const target = callTarget(checker, signature);
    if (target) {
      overridden.push(target);
    }
    queue.push(...(base.getBaseTypes?.() ?? []));
  }
  return overridden;
}

/** The declaration the node names or is, so annotations and nullness read the declared symbol. */
function declarationOf(checker, node) {
  if (ts.isClassDeclaration(node) || ts.isInterfaceDeclaration(node) || ts.isMethodDeclaration(node) || ts.isParameter(node) || ts.isPropertyDeclaration(node)) {
    return node;
  }
  return checker.getSymbolAtLocation(node)?.valueDeclaration;
}

function decoratorNames(checker, declaration) {
  return (ts.canHaveDecorators(declaration) ? ts.getDecorators(declaration) ?? [] : []).map((decorator) => {
    const expression = ts.isCallExpression(decorator.expression) ? decorator.expression.expression : decorator.expression;
    return checker.getSymbolAtLocation(expression)?.getName() ?? expression.getText();
  });
}

function constantOf(checker, node, type) {
  const value = ts.isEnumMember(node) || ts.isPropertyAccessExpression(node) ? checker.getConstantValue(node) : undefined;
  const literal = value ?? (type.isLiteral() && !(type.flags & ts.TypeFlags.BigIntLiteral) ? type.value : undefined);
  if (typeof literal === 'number') {
    return literal;
  }
  if (typeof literal === 'string') {
    return literal;
  }
  return type.flags & ts.TypeFlags.BooleanLiteral ? checker.typeToString(type) === 'true' : undefined;
}

function factsFor(checker, node, kinds) {
  const type = checker.getTypeAtLocation(node);
  const facts = {};
  if (kinds.includes('type')) {
    facts.type = typeRef(checker, type);
  }
  const declaration = declarationOf(checker, node);
  if (kinds.includes('shape') && declaration) {
    facts.shape = shapeOf(checker, declaration);
  }
  if (kinds.includes('call') && (ts.isCallExpression(node) || ts.isNewExpression(node))) {
    facts.call = callTarget(checker, checker.getResolvedSignature(node));
  }
  if (kinds.includes('annotations') && declaration) {
    facts.annotations = decoratorNames(checker, declaration);
    facts.scopes = [];
    for (let scope = declaration.parent; scope; scope = scope.parent) {
      facts.scopes.push(...decoratorNames(checker, scope));
    }
  }
  if (kinds.includes('overrides') && declaration && ts.isMethodDeclaration(node)) {
    facts.overrides = overridesOf(checker, declaration);
  }
  if (kinds.includes('constant')) {
    facts.constant = constantOf(checker, node, type);
  }
  // TypeScript has no checked exceptions: "thrown" is never answered.
  return Object.fromEntries(Object.entries(facts).filter(([, value]) => value !== undefined));
}

function factsOf(request) {
  const facts = {};
  const kinds = Array.isArray(request.kinds) ? request.kinds : [];
  if (!Array.isArray(request.spans) || request.spans.length === 0 || kinds.length === 0) {
    return facts;
  }
  const file = resolve(projectRoot, request.path);
  if (JAVASCRIPT_FILE.test(file) && !options.allowJs) {
    return facts;
  }
  texts.set(file, String(request.text ?? ''));
  versions.set(file, (versions.get(file) ?? 0) + 1);
  const program = service.getProgram();
  const sourceFile = program?.getSourceFile(file);
  if (!sourceFile) {
    return facts;
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
    const found = factsFor(checker, node, kinds);
    if (Object.keys(found).length > 0) {
      facts[`${start}:${end}`] = found;
    }
  }
  return facts;
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
  let facts = {};
  try {
    facts = factsOf(request);
  } catch {
    process.stderr.write('type analysis failed for one file\n');
  }
  process.stdout.write(`${JSON.stringify({ facts })}\n`);
});
