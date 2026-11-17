def f():
    value = 1
    print(value)
try:
    import json
except ImportError:
    json = None
print(json)


def keyed(rows):
    pairs = {email.name: email for email in rows}
    email = "last"
    return pairs, email
