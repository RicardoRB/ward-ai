import hashlib
import os

hashlib.md5(data, usedforsecurity=False)

name = hashlib.md5(content).hexdigest() + ".bin"


def named_by_path(data, root):
    return os.path.join(root, hashlib.md5(data).hexdigest())


def named_by_variable(data):
    h = hashlib.sha1()
    h.update(data)
    upload = h.hexdigest()
    open(upload, "rb")


def keyed(cache, key, value):
    cache[hashlib.md5(key).hexdigest()] = value


def hash_file(fp, algo="sha1"):
    fp.seek(0)
    if algo == "sha1":
        h = hashlib.sha1()
        while chunk := fp.read(1024):
            h.update(chunk)
        fp.seek(0)
        return h.hexdigest()
    raise NotImplementedError


def upload_file(file_obj, model):
    model_args = {"type": "standard"}
    sha1sum = hash_file(fp=file_obj)
    model_args["sha1sum"] = sha1sum
    return model(**model_args)
