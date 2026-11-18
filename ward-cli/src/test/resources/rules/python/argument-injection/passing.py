import subprocess
from flask import request


def numeric():
    count = int(request.args["count"])
    subprocess.run(["head", "-n", str(count), "notes.txt"])


def fixed():
    subprocess.run(["ls", "-l"])


def shell_string():
    subprocess.run(request.args["cmd"], shell=True)
