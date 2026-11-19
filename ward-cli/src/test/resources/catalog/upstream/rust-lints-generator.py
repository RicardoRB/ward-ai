#!/usr/bin/env python3
"""Writes clippy-<version>.txt and rustc-<version>.txt from the tools' own lint listings.

Run it in this folder with clippy-driver and rustc on PATH: python3 rust-lints-generator.py
"""
import re
import subprocess

GROUPS = ["correctness", "suspicious", "style", "complexity", "perf", "pedantic", "restriction", "nursery", "cargo"]


def run(*cmd):
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout


def lints(text, prefix):
    found = {}
    for line in text.splitlines():
        match = re.match(r"\s+(%s[a-z0-9-]+)\s+(allow|warn|deny|forbid)\s" % re.escape(prefix), line)
        if match:
            found[match.group(1)] = match.group(2)
    return found


def groups(text):
    result = {}
    for line in text.splitlines():
        match = re.match(r"\s+clippy::(\w+)\s+(clippy::.*)", line)
        if match and match.group(1) in GROUPS:
            for lint in match.group(2).split(", "):
                result[lint.strip()] = match.group(1)
    return result


def version(command):
    return re.search(r"(\d+\.\d+\.\d+)", run(*command, "--version")).group(1)


def write(tool, ver, header, rows):
    with open("%s-%s.txt" % (tool, ver), "w") as out:
        out.write(header)
        for name, group in sorted(rows.items()):
            out.write("%s\t%s\n" % (name, group))


driver = run("clippy-driver", "-W", "help")
clippy = {name: groups(driver)[name] for name in lints(driver, "clippy::")}
clippy_version = version(["clippy-driver"])
write(
    "clippy",
    clippy_version,
    "# Clippy %s lint identifiers (group = Clippy lint group).\n"
    "# Generated with: clippy-driver -W help; every name under 'Lint checks loaded by this crate' with the group of 'Lint groups loaded by this crate' (rust-lints-generator.py).\n"
    "# format: <lint><TAB><group>\n" % clippy_version,
    clippy,
)
rustc = lints(run("rustc", "-W", "help"), "")
rustc_version = version(["rustc"])
write(
    "rustc",
    rustc_version,
    "# rustc %s lint identifiers (group = the lint's default level).\n"
    "# Generated with: rustc -W help; every name under 'Lint checks provided by rustc' (rust-lints-generator.py).\n"
    "# format: <lint><TAB><default level>\n" % rustc_version,
    rustc,
)
