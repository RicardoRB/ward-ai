from flask import redirect, request
from django.http import HttpResponseRedirect


def go():
    target = request.args.get("next")
    return redirect(target)


def go_django(request):
    return HttpResponseRedirect(request.GET["next"])
