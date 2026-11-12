import requests


def constant():
    return requests.get("https://api.example.com/status", params=request.args)


def checked(url):
    return requests.get(url)
