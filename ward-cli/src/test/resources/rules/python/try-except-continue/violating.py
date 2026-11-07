for x in y:
    try:
        f()
    except ValueError:
        continue
