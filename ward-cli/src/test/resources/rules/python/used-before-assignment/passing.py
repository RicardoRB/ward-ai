def f():
    value = 1
    print(value)
try:
    import json
except ImportError:
    json = None
print(json)
