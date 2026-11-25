import hashlib
import os


def stored(data, args):
    args["sum"] = hashlib.sha1(data).hexdigest()


def sent(data):
    send(hashlib.md5(data).hexdigest())


def key(data):
    h = hashlib.sha1()
    h.update(data)
    return h.hexdigest()


def save(data, root):
    return os.path.join(root, key(data))
