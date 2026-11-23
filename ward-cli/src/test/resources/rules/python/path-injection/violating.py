import os
import shutil
from flask import Flask, request, send_file

app = Flask(__name__)


@app.route("/download")
def download():
    name = request.args.get("file")
    return send_file("/srv/files/" + name)


@app.route("/read")
def read():
    path = request.args["path"]
    with open(path) as handle:
        return handle.read()


def remove():
    shutil.rmtree(request.form["folder"])


@app.route("/other/<name>")
def other(name):
    if name not in ("a.txt", "b.txt"):
        print("unknown")
    return send_file(os.path.join("/srv/files", name))


@app.route("/param/<name>")
def param(name, allowed):
    if name in allowed:
        return send_file(os.path.join("/srv/files", name))
    return "missing", 404


@app.route("/request/<name>")
def from_request(name):
    names = request.args.getlist("allowed")
    if name in names:
        return send_file(os.path.join("/srv/files", name))
    return "missing", 404
