def run(a):
    try:
        a.one()
        a.two()
    except ValueError:
        return None
