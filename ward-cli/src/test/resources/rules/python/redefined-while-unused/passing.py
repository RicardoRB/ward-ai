def f():
    pass
f()


def f():
    pass


import email
import email.policy

if email.policy:
    from json import JSONDecoder as Decoder
else:
    from simplejson import JSONDecoder as Decoder

try:
    import tomllib as toml
except ImportError:
    import tomli as toml
