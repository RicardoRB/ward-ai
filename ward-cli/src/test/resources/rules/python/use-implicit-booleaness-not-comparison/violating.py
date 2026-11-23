items = []
if items == []:
    pass


def read(request):
    value = request.POST.get("name", "")
    if value == "":
        return None


def split(spec):
    if spec.channels is None:
        return None
    for s in spec.channels.split(","):
        if s == "":
            return None
