def run(a):
    try:
        a.one()
    except ValueError:
        a.two()
        a.three()
