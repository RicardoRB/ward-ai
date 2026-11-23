def f(flag):
    if flag:
        result = 1
    return result


def local_abort(a):
    if a:
        z = 1
    else:
        abort(404)
    return z
