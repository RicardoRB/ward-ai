import hashlib

PASSWORD_HASHERS = ["django.contrib.auth.hashers.Argon2PasswordHasher"]


def store(password, salt):
    return hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1)


def checksum(data):
    return hashlib.sha256(data).hexdigest()
