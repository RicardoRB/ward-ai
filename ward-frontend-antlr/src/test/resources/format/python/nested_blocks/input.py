def run(items):
    for i in items:
        if i>0 and not i==3:
            print(i)
        elif i<0:
            continue
        else:
            pass
    while True:
        try:
            with open("f") as fh:
                data=fh.read()
        except OSError as err:
            raise err
        finally:
            done()
