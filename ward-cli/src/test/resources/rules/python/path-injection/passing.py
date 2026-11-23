import os
from flask import Flask, request, send_file
from werkzeug.utils import secure_filename

app = Flask(__name__)


@app.route("/download")
def download():
    name = secure_filename(request.args.get("file"))
    return send_file(os.path.join("/srv/files", name))


@app.route("/read")
def read():
    name = os.path.basename(request.args["path"])
    with open("/srv/files/" + name) as handle:
        return handle.read()


def fixed():
    with open("/etc/hostname") as handle:
        return handle.read()


ALLOWED = ("a.txt", "b.txt")


@app.route("/known/<name>")
def known(name):
    if name in ALLOWED:
        return send_file(os.path.join("/srv/files", name))
    return "missing", 404


@app.route("/checked/<name>")
def checked(name):
    if name not in ALLOWED:
        raise ValueError(name)
    with open("/srv/files/" + name) as handle:
        return handle.read()


@app.route("/listed/<name>")
def listed(name):
    names = [entry for entry in os.listdir("/srv/files") if entry.endswith(".txt")]
    if name in names:
        return send_file(os.path.join("/srv/files", name))
    return "missing", 404


@app.route("/inline/<name>")
def inline(name):
    if name in {"a.txt", "b.txt"}:
        return send_file(os.path.join("/srv/files", name))
    return "missing", 404
