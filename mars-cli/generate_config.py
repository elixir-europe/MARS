import configparser
import pathlib


def create_settings_file(settings_dir):
    """
    Create a settings file with the specified log path and settings path.

    Args:
        settings_path (str): The path to the settings file.

    Returns:
        None
    """
    log_path = settings_dir / "app.log"
    settings_path = settings_dir / "settings.ini"
    config = configparser.ConfigParser()
    config["logging"] = {
        "log_level": "ERROR",
        "log_file": log_path,
        "log_max_size": "1024",
        "log_max_files": "5",
    }

    with open(settings_path, "w") as config_file:
        config.write(config_file)


def generate_config(overwrite):
    """
    Generate the configuration file for the MARS CLI.

    Returns:
        None
    """
    settings_dir = pathlib.Path.home() / ".mars"
    if not settings_dir.exists():
        settings_dir.mkdir()

    settings_path = settings_dir / "settings.ini"

    if settings_path.exists() and not overwrite:
        return

    create_settings_file(settings_dir)
