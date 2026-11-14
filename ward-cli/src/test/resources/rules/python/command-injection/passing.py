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
