import os
import tarfile


def read():
    return open(os.path.join("/srv/files", request.args["name"]))


def unpack(path):
    with tarfile.open(path) as tar:
        tar.extractall("/srv/files")
