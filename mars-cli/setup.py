import pathlib
from _version import __version__
from setuptools import find_packages, setup

with open("requirements.txt", "r") as file:
    required_deps = file.read().splitlines()

parent_folder = pathlib.Path(__file__).parent.resolve()
long_description = (parent_folder / "README.md").read_text(encoding="utf-8")

setup(
    name="mars",
    description="Multi-omics Adapter for Repository Submissions",
    long_description=long_description,
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
