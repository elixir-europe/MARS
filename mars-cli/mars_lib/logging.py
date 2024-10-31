import click
import logging
import sys


def print_and_log(msg, level="info"):
    if level == "info":
        click.echo(msg)
        logging.info(msg)
    elif level == "error":
        click.echo(msg, file=sys.stderr)
        logging.error(msg)
    elif level == "warning":
        click.echo(msg)
        logging.warning(msg)
    else:
        click.echo(msg)
        logging.debug(msg)
