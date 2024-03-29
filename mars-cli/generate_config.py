import configparser
import pathlib

# Create settings file in user's home directory
settings_dir = pathlib.Path.home() / ".mars"
if not settings_dir.exists():
    settings_dir.mkdir()

settings_path = settings_dir / "settings.ini"
log_path = settings_dir / "app.log"

config = configparser.ConfigParser()
config["logging"] = {"log_level": "ERROR", "log_file": log_path}

with open(settings_path, "w") as config_file:
    config.write(config_file)
