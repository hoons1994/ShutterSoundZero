"""Read the one expected JSON entry in memory; never extract artifact paths."""

import io
import stat
import sys
import zipfile
import zlib

MAX_ARCHIVE_BYTES = 2 * 1024 * 1024
MAX_JSON_BYTES = 8 * 1024 * 1024
EXPECTED_ENTRY = "dependency-graph-reports/dependency_submission-generate.json"


def read_snapshot(archive_bytes):
    if len(archive_bytes) > MAX_ARCHIVE_BYTES:
        raise ValueError("archive exceeds size limit")
    with zipfile.ZipFile(io.BytesIO(archive_bytes)) as archive:
        entries = archive.infolist()
        if len(entries) != 1:
            raise ValueError("expected exactly one snapshot JSON entry")
        entry = entries[0]
        mode = entry.external_attr >> 16
        if entry.filename != EXPECTED_ENTRY or entry.is_dir():
            raise ValueError("unexpected artifact entry path")
        if stat.S_IFMT(mode) not in (0, stat.S_IFREG):
            raise ValueError("snapshot entry must be a regular file")
        if entry.flag_bits & 1:
            raise ValueError("encrypted entries are not accepted")
        if entry.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
            raise ValueError("unsupported archive compression")
        if not 0 < entry.file_size <= MAX_JSON_BYTES:
            raise ValueError("snapshot JSON exceeds size limit")
        # ZipExtFile validates the CRC when read through EOF.
        with archive.open(entry) as source:
            content = source.read(MAX_JSON_BYTES + 1)
        if len(content) != entry.file_size or len(content) > MAX_JSON_BYTES:
            raise ValueError("snapshot JSON size does not match archive metadata")
        content.decode("utf-8", errors="strict")
        return content


if __name__ == "__main__":
    try:
        sys.stdout.buffer.write(read_snapshot(sys.stdin.buffer.read(MAX_ARCHIVE_BYTES + 1)))
    except (ValueError, OSError, zipfile.BadZipFile, RuntimeError, NotImplementedError, EOFError, zlib.error):
        sys.exit("Invalid dependency snapshot archive")
