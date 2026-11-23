def f(flag):
    if flag:
        result = 1
    else:
        result = 2
    return result
try:
    import json
except ImportError:
    json = None
print(json)


from flask import abort
import sys


def view(a, b):
    if a:
        x = 1
    elif b:
        x = 2
    else:
        abort(404)
    return x


def stop(a):
    if a:
        y = 1
    else:
        sys.exit(1)
    return y
