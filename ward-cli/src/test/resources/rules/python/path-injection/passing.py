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
