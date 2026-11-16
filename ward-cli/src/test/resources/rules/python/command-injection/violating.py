import os
import subprocess as sp
from flask import request
from subprocess import check_output


def ping():
    host = request.args.get("host")
    os.system("ping -c 1 " + host)


def listing():
    folder = request.form["folder"]
    command = f"ls {folder}"
    sp.run(command, shell=True)


def from_import():
    return check_output(request.values.get("cmd"), shell=True)


def shell_string():
    pattern = request.args.get("pattern")
    sp.call("grep -r " + pattern + " .", shell=True)


def shell_list():
    sp.Popen([request.args.get("cmd")], shell=True)
