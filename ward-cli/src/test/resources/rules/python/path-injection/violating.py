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
