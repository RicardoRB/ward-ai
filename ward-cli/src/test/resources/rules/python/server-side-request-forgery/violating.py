import requests


def direct():
    return requests.get(request.args["url"])


def joined():
    return requests.post("http://" + request.form["host"] + "/hook")
