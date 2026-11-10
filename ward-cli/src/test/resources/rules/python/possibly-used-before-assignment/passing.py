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
