class MyError(ValueError):
    pass


try:
    pass
except Exception:
    pass
except ValueError:
    pass
try:
    pass
except ValueError:
    pass
except MyError:
    pass
