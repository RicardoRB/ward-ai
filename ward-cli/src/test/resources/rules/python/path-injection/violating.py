import os
import shutil
from io import BytesIO
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


def build_named(name):
    return os.path.join("/srv/files", name)


@app.route("/exported")
def export_named():
    table = request.args.get("table")
    output = build_named(table)
    return send_file(output, as_attachment=True)


@app.route("/maybe")
def export_maybe():
    table = request.args.get("table")
    output = BytesIO()
    if table:
        output = table
    return send_file(output, as_attachment=True)


FILES = {"a": "/srv/files/a.txt"}


@app.route("/lookup")
def lookup_file():
    key = request.args.get("key")
    picked = FILES.get(key, key)
    return send_file(picked)


@app.route("/lookup-param")
def lookup_param(table):
    key = request.args.get("key")
    picked = table[key]
    return send_file(picked)


def name_of(table):
    return table


@app.route("/echo")
def echo():
    table = request.args.get("table")
    return send_file(name_of(table))
