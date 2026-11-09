def combine(left, middle=1, /, right=2, *, flag=False):
    return left + middle + right

def lookup(key, /, **options):
    return options.get(key)

scale = lambda value, /, factor=2: value * factor
