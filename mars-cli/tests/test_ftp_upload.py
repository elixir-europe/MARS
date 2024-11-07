import json

import pytest
from pathlib import Path
import ftplib

from mars_lib.ftp_upload import FTPUploader


def test_upload_login_failure():
    uploader = FTPUploader("webin2.ebi.ac.uk", "junk", "more junk")
    with pytest.raises(ftplib.error_perm, match="530 Login incorrect."):
        uploader.upload([Path("./tests/fixtures/not_a_json_file.txt")])


@pytest.mark.skip(
    reason="Relies on real ENA credentials in test_credentials_example.json"
)
def test_upload_success():
    # For local testing, add ENA username/password to test_credentials_example.json
    with open("./tests/test_credentials_example.json") as f:
        creds = json.load(f)
    uploader = FTPUploader("webin2.ebi.ac.uk", creds["username"], creds["password"])
    uploader.upload(
        [
            Path("../test-data/ENA_TEST2.R1.fastq.gz"),
            Path("./tests/fixtures/not_a_json_file.txt"),
        ]
    )
