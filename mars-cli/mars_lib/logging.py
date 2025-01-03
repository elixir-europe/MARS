import logging
import colorlog
from configparser import ConfigParser
from logging.handlers import RotatingFileHandler
from pathlib import Path

logger = logging.getLogger("MARS-CLI")


def print_and_log(msg, level="info"):
    if level == "info":
        logger.info(msg)
    elif level == "warning":
        logger.warning(msg)
    elif level == "error":
        logger.error(msg)
    elif level == "critical":
        logger.critical(msg)
    else:
        logger.debug(msg)


def init_logging(config: ConfigParser, fallback_log_file: Path) -> None:
    # Logging configuration
    log_level = config.get("logging", "log_level", fallback="ERROR")
    log_file = config.get("logging", "log_file", fallback=fallback_log_file)
    log_max_size = int(
        config.get("logging", "log_max_size", fallback="1024")
    )  # in kilobytes. 1 MB by default.
    log_max_files = int(
        config.get("logging", "log_max_files", fallback="5")
    )  # number of backup files. 5 by default.
    file_handler = RotatingFileHandler(
        log_file, maxBytes=log_max_size * 1024, backupCount=log_max_files
    )
    file_handler.setFormatter(
        logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - %(message)s")
    )

    stream_handler = colorlog.StreamHandler()
    stream_handler.setFormatter(
        colorlog.ColoredFormatter(
            "%(log_color)s%(asctime)s - %(name)s - %(levelname)s - %(message)s%(reset)s",
            log_colors={
                "DEBUG": "bold_cyan",
                "INFO": "green",
                "WARNING": "yellow",
                "ERROR": "red",
                "CRITICAL": "red,bg_white",
            },
            style="%",
        )
    )
    logging.basicConfig(handlers=[file_handler, stream_handler], level=log_level)
