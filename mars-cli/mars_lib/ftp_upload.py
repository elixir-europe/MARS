import ftplib
import os
from pathlib import Path
from typing import List

from retry import retry
from mars_lib.logging import print_and_log


class PatchFTP_TLS(ftplib.FTP_TLS):
    """
    Modification from https://stackoverflow.com/questions/14659154/ftpes-session-reuse-required
    to work around bug in Python standard library: https://bugs.python.org/issue19500
    Explicit FTPS, with shared TLS session
    """

    def ntransfercmd(self, cmd, rest=None):
        conn, size = ftplib.FTP.ntransfercmd(self, cmd, rest)
        if self._prot_p:
            conn = self.context.wrap_socket(
                conn, server_hostname=self.host, session=self.sock.session
            )  # this is the fix
        return conn, size


class FTPUploader:
    def __init__(self, ftp_host: str, username: str, password: str):
        self.ftp_host = ftp_host
        self.username = username
        self.password = password

    @retry(exceptions=ftplib.all_errors, tries=3, delay=2, backoff=1.2, jitter=(1, 3))
    def upload(self, file_paths: List[Path], target_location: str = "/") -> bool:
        # Heuristic to set the expected timeout assuming 10Mb/s upload speed but no less than 30 sec
        # and no more than an hour
        max_file_size = max([os.path.getsize(f) for f in file_paths])
        timeout = min(max(int(max_file_size / 10000000), 30), 3600)
        with PatchFTP_TLS() as ftps:
            ftps.context.set_ciphers("HIGH:!DH:!aNULL")
            ftps.connect(self.ftp_host, port=21, timeout=timeout)
            ftps.login(self.username, self.password)
            ftps.prot_p()

            ftps.cwd(target_location)
            previous_content = ftps.nlst()
            for file_to_upload in file_paths:
                file_name = os.path.basename(file_to_upload)
                if file_name in previous_content and ftps.size(
                    file_name
                ) == os.path.getsize(file_to_upload):
                    print_and_log(
                        f"{file_name} already exists and has the same size on the FTP, skipping"
                    )
                    continue
                print_and_log(f"Uploading {file_name} to FTP")
                with open(file_to_upload, "rb") as open_file:
                    ftps.storbinary("STOR %s" % file_name, open_file)

        return True
