from flask import redirect, request
from django.http import HttpResponseRedirect


def go():
    target = request.args.get("next")
    return redirect(target)


def go_django(request):
    return HttpResponseRedirect(request.GET["next"])


def go_before_query():
    target = request.args.get("next")
    return redirect(target + "?done=1")


def go_f_before_query():
    target = request.args.get("next")
    return redirect(f"{target}?done=1")


def go_through_helper(request):
    return redirect(host_and_query(request))


def host_and_query(request):
    return request.args.get("next") + "?done=1"


def go_plain_tail(request):
    tail = request.args.get("tail")
    return redirect(tail + "/more?x=1")
