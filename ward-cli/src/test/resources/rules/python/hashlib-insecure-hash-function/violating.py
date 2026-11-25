import hashlib
import hmac
import os


def never_used(data):
    hashlib.md5(data)


def returned(data):
    return hashlib.md5(data).hexdigest()


def login(password, stored):
    return hashlib.md5(password).hexdigest() == stored


def sign(data, expected):
    return hmac.compare_digest(hashlib.sha1(data).hexdigest(), expected)


def make_key(data):
    h = hashlib.sha1()
    h.update(data)
    return h.hexdigest()


def check(data, stored):
    return make_key(data) == stored


def named_and_compared(data, stored, root):
    name = hashlib.md5(data).hexdigest()
    open(os.path.join(root, name))
    return name == stored
