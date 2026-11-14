import requests
from flask import request


def fixed():
    return requests.get("https://api.example.com/items", timeout=5).text


def query_only():
    term = request.args.get("q")
    return requests.get("https://api.example.com/search", params={"q": term}, timeout=5)
