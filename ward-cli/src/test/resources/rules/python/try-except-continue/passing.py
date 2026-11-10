for x in y:
    try:
        f()
    except ValueError:
        log()
        continue
