def run(job):
    try:
        try:
            try:
                job()
            except ValueError:
                pass
        except ValueError:
            pass
    except ValueError:
        pass
