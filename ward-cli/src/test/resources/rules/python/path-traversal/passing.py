import os
import tarfile


def read():
    return open("/srv/files/report.txt")


def unpack(path):
    with tarfile.open(path) as tar:
        tar.extractall("/srv/files", filter="data")
