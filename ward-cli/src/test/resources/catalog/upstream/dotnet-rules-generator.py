#!/usr/bin/env python3
"""Writes the pinned rule lists of the .NET analyzers, the code-style rules and StyleCop.

Usage: dotnet-rules-generator.py <dir with the unpacked nupkg netanalyzers and the lister output ide.tsv, sa.tsv> <out dir>
Lister: `dotnet run --project dotnet-analyzer-lister -- <analyzers/dotnet/cs dir> > ide.tsv` (same for the StyleCop dir > sa.tsv).
NuGet packages (all MIT): Microsoft.CodeAnalysis.NetAnalyzers 9.0.0, Microsoft.CodeAnalysis.CSharp.CodeStyle
4.14.0, StyleCop.Analyzers.Unstable 1.2.0.556. CA ids come from the package's sarif file; IDE and SA ids are
the SupportedDiagnostics of the analyzer assemblies (dotnet-analyzer-lister).
"""
import json, re, sys, pathlib

src, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])

def ids(path, pat):
    data = path.read_bytes()
    return set(re.findall(pat, data.decode("utf-16le", errors="ignore"))) | set(re.findall(pat, data.decode("latin1")))

def write(name, version, tool, command, rows):
    lines = [f"# {tool} {version} rule identifiers (group = category).", f"# Generated with: {command}", "# format: <rule id><TAB><group>"]
    lines += [f"{rid}\t{group}" for rid, group in sorted(rows.items())]
    (out / f"{name}-{version}.txt").write_text("\n".join(lines) + "\n")

sarif = json.loads((src / "netanalyzers/documentation/Microsoft.CodeAnalysis.NetAnalyzers.sarif").read_text("utf-8-sig"))
items = [(rid, r) for run in sarif["runs"] for rid, r in run["rules"].items()]
ca = {rid: r.get("properties", {}).get("category", "Misc") for rid, r in items if rid.startswith("CA")}
write("dotnet-analyzers", "9.0.0", "Microsoft.CodeAnalysis.NetAnalyzers",
      "dotnet-rules-generator.py: every CAxxxx rule of documentation/Microsoft.CodeAnalysis.NetAnalyzers.sarif in the nupkg", ca)
def listed(name, prefix):
    rows = (line.split("\t") for line in (src / name).read_text().splitlines())
    return {rid: category.split(".")[-1].removesuffix("Rules") for rid, category in rows if re.fullmatch(prefix + r"\d{4}", rid)}

write("dotnet-code-style", "4.14.0", "Microsoft.CodeAnalysis.CSharp.CodeStyle",
      "dotnet-analyzer-lister (loads the CodeStyle analyzers and prints SupportedDiagnostics), then dotnet-rules-generator.py", listed("ide.tsv", "IDE"))
write("stylecop", "1.2.0.556", "StyleCop.Analyzers",
      "dotnet-analyzer-lister (loads StyleCop.Analyzers.dll and prints SupportedDiagnostics), then dotnet-rules-generator.py", listed("sa.tsv", "SA"))
