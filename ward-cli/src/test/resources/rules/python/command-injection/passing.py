import os
import shlex
import subprocess
from flask import request


def ping():
    host = shlex.quote(request.args.get("host"))
    os.system("ping -c 1 " + host)


def constant():
    subprocess.run("ls -l", shell=True)


def numeric():
    count = int(request.args.get("n"))
    os.system("head -n " + str(count) + " log.txt")


def argument_vector():
    folder = request.form["folder"]
    subprocess.run(["ls", "-l", folder])


def argument_vector_without_shell():
    name = request.args.get("name")
    subprocess.check_output(("grep", "-r", name, "."), shell=False)
