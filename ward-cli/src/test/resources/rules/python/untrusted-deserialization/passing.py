import json
import pickle
import yaml
from flask import request


def restore():
    return json.loads(request.get_data())


def configure():
    document = request.form["config"]
    return yaml.safe_load(document)


def from_disk(path):
    with open(path, "rb") as handle:
        return pickle.load(handle)
