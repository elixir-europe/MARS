import pytest
from pathlib import Path
import ftplib

from mars_lib.ftp_upload import FTPUploader


def test_upload_login_failure():
    uploader = FTPUploader("webin2.ebi.ac.uk", "junk", "more junk")
    with pytest.raises(ftplib.error_perm, match="530 Login incorrect."):
        uploader.upload([Path("./tests/fixtures/not_a_json_file.txt")])
