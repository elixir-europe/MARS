from _version import __version__
from setuptools import find_packages, setup

with open("requirements.txt", "r") as file:
    required_deps = file.read().splitlines()

setup(
    name="mars",
    description="Multi-omics Adapter for Repository Submissions",
    packages=find_packages(include=["mars-lib"]),
    version=__version__,
    license="MIT",
    install_requires=[required_deps],
    project_urls={
        "Source": "https://github.com/elixir-europe/MARS",
        "Bug Reports": "https://github.com/elixir-europe/MARS/issues",
    },
    python_requires=">=3.10, <4",
)
