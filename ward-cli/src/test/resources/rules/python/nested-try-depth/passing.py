def run(job):
    try:
        try:
            job()
        except ValueError:
            pass
    except ValueError:
        pass
