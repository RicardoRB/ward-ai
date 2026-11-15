import requests
import urllib.request
from flask import request


def proxy():
    url = request.args.get("url")
    return requests.get(url, timeout=5).text


def fetch():
    target = request.form["target"]
    return urllib.request.urlopen(target).read()


def direct():
    return requests.post(request.json["callback"], timeout=5)
