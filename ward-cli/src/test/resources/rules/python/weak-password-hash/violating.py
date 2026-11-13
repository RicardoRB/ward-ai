import hashlib

PASSWORD_HASHERS = ["django.contrib.auth.hashers.MD5PasswordHasher"]


def store(password):
    return hashlib.sha256(password.encode()).hexdigest()


def hash_it(make_password, password):
    return make_password(password, hasher="md5")
