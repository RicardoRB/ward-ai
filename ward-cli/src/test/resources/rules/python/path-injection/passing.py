import csv
import os
from io import BytesIO, StringIO
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


def dump_users(admin=False):
    output = BytesIO()
    output.write(b"id\n")
    output.seek(0)
    return output


DUMPERS = {"users": dump_users, "admins": lambda: dump_users(True)}


def build_table(tablename):
    model = lookup_model(tablename)
    temp = StringIO()
    writer = csv.writer(temp)
    writer.writerow(model.columns)
    output = BytesIO()
    output.write(temp.getvalue().encode("utf-8"))
    output.seek(0)
    return output


def dump_named(name):
    dump_func = DUMPERS.get(name)
    if dump_func:
        return dump_func()
    elif lookup_model(name):
        return build_table(tablename=name)
    else:
        raise KeyError


@app.route("/export")
def export_table():
    table = request.args.get("table")
    output = dump_named(table)
    return send_file(output, as_attachment=True)


@app.route("/buffer")
def export_buffer():
    table = request.args.get("table")
    output = BytesIO(table.encode("utf-8"))
    return send_file(output, as_attachment=True)
