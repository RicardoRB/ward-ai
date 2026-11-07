try:
    f()
    g()
except OSError:
    pass
try:
    f()
except OSError:
    pass
except ValueError:
    pass
try:
    f()
except OSError:
    pass
else:
    g()
