import pickle
import yaml
from flask import request


def restore():
    blob = request.get_data()
    return pickle.loads(blob)


def configure():
    document = request.form["config"]
    return yaml.load(document, Loader=yaml.Loader)


def direct():
    return pickle.load(request.files["state"])
