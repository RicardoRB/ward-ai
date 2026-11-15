import hashlib

PASSWORD_HASHERS = [
    "django.contrib.auth.hashers.Argon2PasswordHasher",
    # PBKDF2 with SHA-1 is a slow, salted hasher, unlike the plain SHA1PasswordHasher.
    "django.contrib.auth.hashers.PBKDF2SHA1PasswordHasher",
]


def store(password, salt):
    return hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1)


def checksum(data):
    return hashlib.sha256(data).hexdigest()
