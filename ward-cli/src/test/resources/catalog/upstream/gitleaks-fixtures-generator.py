#!/usr/bin/env python3
"""One-off generator of the fixtures of the secret rules (rules/secrets/<rule>/).

Command (the output is committed; the fixtures hold fake secrets only):

    curl -fsSL https://raw.githubusercontent.com/gitleaks/gitleaks/v8.30.1/config/gitleaks.toml \
        -o /tmp/gitleaks.toml
    python3 -I ward-cli/src/test/resources/catalog/upstream/gitleaks-fixtures-generator.py \
        /tmp/gitleaks.toml ward-cli/src/test/resources/rules/secrets [seeds.txt]

For each rule it samples a string from the rule's regex with a fixed seed, so a secret is a random
draw from the characters the regex allows and never a real credential. A candidate must match the
regex at its start, have the entropy the rule asks for, and not be allowlisted; the first seed that
does is used. seeds.txt (optional, "<rule> <first seed>" per line) restarts a rule at another seed.
The passing fixture is a form that the rule does not report: a low-entropy or truncated variant,
or a form its allowlist names.
"""
import math
import random
import re
import sys
import tomllib
from collections import Counter
from pathlib import Path
from re import _constants as C
from re import _parser

PRINTABLE = [chr(c) for c in range(32, 127)] + ["\t", "\n"]
MULTILINE = {"curl-auth-header", "curl-auth-user", "jwt-base64", "kubernetes-secret-yaml", "private-key"}
EXTRA_PASSING = {
    "aws-access-token": "AKIAIOSFODNN7EXAMPLE\n",
    "gcp-api-key": 'key = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567"\n',
    "generic-api-key": 'api_key = "qwertzuiopasdfgh"\n',
    "vault-service-token": "token = s.qwertzuiopasdfghjklyxcvb\n",
}
# A form no sampler draws: a Kubernetes Secret has to be a manifest (violating, expected, passing).
MANUAL = {
    "kubernetes-secret-yaml": (
        "apiVersion: v1\nkind: Secret\nmetadata:\n  name: app\ndata:\n"
        "  pass"
        "word: cGFzc3dvcmQxMjM0NTY3OA==\n",
        "2:1",
        "apiVersion: v1\nkind: Secret\nmetadata:\n  name: app\ndata:\n"
        "  password: {{ .Values.password }}\n",
    ),
}
EXTENSIONS = {
    "freemius-secret-key": "php",
    "hashicorp-tf-password": "tf",
    "kubernetes-secret-yaml": "yaml",
    "nuget-config-password": "nuget.config",
    "pkcs12-file": "p12",
}


def entropy(text: str) -> float:
    if not text:
        return 0.0
    inverse = 1.0 / len(text.encode())
    return -sum((n * inverse) * math.log2(n * inverse) for n in Counter(text).values())


class Sampler:
    def __init__(self, rng: random.Random, newlines: bool):
        self.rng = rng
        self.printable = PRINTABLE if newlines else [c for c in PRINTABLE if c != "\n"]

    def sample(self, parsed) -> str:
        return "".join(self.item(op, arg) for op, arg in parsed)

    def item(self, op, arg) -> str:
        if op is C.LITERAL:
            return chr(arg)
        if op is C.NOT_LITERAL:
            return self.pick([c for c in self.printable if ord(c) != arg and c != "\n"])
        if op is C.ANY:
            return self.pick([c for c in self.printable if c != "\n"])
        if op is C.IN:
            return self.pick(self.chars(arg))
        if op is C.BRANCH:
            return self.sample(self.rng.choice(arg[1]))
        if op is C.SUBPATTERN:
            return self.sample(arg[3])
        if op in (C.MAX_REPEAT, C.MIN_REPEAT):
            low, high, body = arg
            high = low + 12 if high == C.MAXREPEAT or high > low + 12 else high
            return "".join(self.sample(body) for _ in range(self.rng.randint(low, high)))
        if op is C.AT:
            return ""
        raise ValueError(f"unsupported {op}")

    def pick(self, chars) -> str:
        return self.rng.choice(chars)

    def chars(self, items) -> list:
        allowed, negate = set(), False
        for op, arg in items:
            if op is C.NEGATE:
                negate = True
            elif op is C.LITERAL:
                allowed.add(chr(arg))
            elif op is C.RANGE:
                allowed.update(chr(c) for c in range(arg[0], arg[1] + 1))
            elif op is C.CATEGORY:
                allowed.update(c for c in self.printable if CATEGORIES[arg](c))
        result = [c for c in self.printable if (c in allowed) != negate]
        return result or ["a"]


CATEGORIES = {
    C.CATEGORY_DIGIT: str.isdigit,
    C.CATEGORY_NOT_DIGIT: lambda c: not c.isdigit(),
    C.CATEGORY_SPACE: lambda c: c in " \t\n",
    C.CATEGORY_NOT_SPACE: lambda c: c not in " \t\n",
    C.CATEGORY_WORD: lambda c: c.isalnum() and c.isascii() or c == "_",
    C.CATEGORY_NOT_WORD: lambda c: not (c.isalnum() and c.isascii() or c == "_"),
}


def go_to_python(regex: str) -> str:
    regex = regex.replace("[[:alnum:]]", "[A-Za-z0-9]")
    if "(?i)" in regex[4:]:
        # Python wants global flags first; Java is the judge of the result.
        regex = "(?i)" + regex.replace("(?i)", "")
    return regex


def compile_all(patterns):
    compiled = []
    for pattern in patterns:
        try:
            compiled.append(re.compile(go_to_python(pattern)))
        except re.error:
            pass
    return compiled


def secret_of(match: re.Match, group: int) -> str:
    if group:
        return match.group(group) or ""
    for value in match.groups():
        if value:
            return value
    return match.group(0)


def allowed(secret: str, match: str, line: str, lists, global_list) -> bool:
    for entry in [global_list] + lists:
        target = {"match": match, "line": line}.get(entry.get("regexTarget", "secret"), secret)
        if any(r.search(target) for r in compile_all(entry.get("regexes", []))):
            return True
        if any(w.lower() in secret.lower() for w in entry.get("stopwords", [])):
            return True
    return False


def candidate(rule: dict, glob: dict, seed: int):
    regex = go_to_python(rule["regex"])
    parsed = _parser.parse(regex)
    pattern = re.compile(regex)
    text = Sampler(random.Random(seed), rule["id"] in MULTILINE).sample(parsed)
    match = pattern.search(text)
    if not match or match.start() != 0 and rule["id"] not in MULTILINE:
        return None
    secret = secret_of(match, rule.get("secretGroup", 0))
    if rule.get("entropy") and entropy(secret) <= rule["entropy"]:
        return None
    line = text.split("\n")[0]
    if allowed(secret, match.group(0), line, rule.get("allowlists", []), glob):
        return None
    return text, match


def passing(rule: dict, text: str, match: re.Match) -> str:
    regex = re.compile(go_to_python(rule["regex"]))
    for cut in (1, 2, 4, 8, 16):
        variant = text[:-cut] if cut < len(text) else ""
        if not regex.search(variant):
            return variant + "\n"
    flat = re.sub(r"[A-Za-z0-9]", "x", text)
    return flat + "\n" if not regex.search(flat) else "no secret here\n"


def position(text: str, offset: int) -> str:
    before = text[:offset]
    return f"{before.count(chr(10)) + 1}:{offset - (before.rfind(chr(10)) + 1) + 1}"


def write(directory: Path, name: str, ext: str, violating: str, expected: str, ok: str) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    (directory / f"violating.{ext}").write_text(violating, encoding="utf-8")
    (directory / "expected.txt").write_text(expected + "\n", encoding="utf-8")
    pass_ext = "txt" if ext in ("p12", "nuget.config") else ext
    (directory / f"passing.{pass_ext}").write_text(ok, encoding="utf-8")


def main() -> None:
    config = tomllib.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    target = Path(sys.argv[2])
    seeds = {}
    if len(sys.argv) > 3:
        seeds = {k: int(v) for k, v in (l.split() for l in Path(sys.argv[3]).read_text().splitlines() if l.strip())}
    failed = []
    for rule in config["rules"]:
        name = rule["id"]
        ext = EXTENSIONS.get(name, "txt")
        if "regex" not in rule:
            write(target / name, name, ext, "placeholder\n", "0:0", "no secret here\n")
            continue
        if name in MANUAL:
            write(target / name, name, ext, MANUAL[name][0], MANUAL[name][1], MANUAL[name][2])
            continue
        found = None
        for seed in range(seeds.get(name, 0), seeds.get(name, 0) + 300):
            try:
                found = candidate(rule, config["allowlist"], seed)
            except (ValueError, re.error, RecursionError):
                break
            if found:
                break
        if not found:
            failed.append(name)
            continue
        text, match = found
        keywords = rule.get("keywords", [])
        if keywords and not any(k.lower() in text.lower() for k in keywords):
            # The keyword prefilter needs a keyword in the file; a comment after the sample adds one.
            text = text + "\n# " + keywords[0]
        ok = EXTRA_PASSING.get(name) or passing(rule, text, match)
        write(target / name, name, ext, text + "\n", position(text, match.start()), ok)
    print("failed:", failed)


if __name__ == "__main__":
    main()
