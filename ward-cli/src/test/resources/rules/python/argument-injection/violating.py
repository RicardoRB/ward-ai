import asyncio
import subprocess
from flask import request


def program():
    tool = request.args["tool"]
    subprocess.run([tool, "--version"])


def option():
    pattern = request.args.get("pattern")
    subprocess.check_output(("grep", pattern, "notes.txt"))


def spawn():
    return asyncio.create_subprocess_exec("convert", request.form["name"])
