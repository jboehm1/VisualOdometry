import hashlib
import io
import stat
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import fetch


class ArchiveSafetyTests(unittest.TestCase):
    def make_zip(self, root, names):
        archive = root / "input.zip"
        with zipfile.ZipFile(archive, "w") as zf:
            for name in names:
                zf.writestr(name, b"small")
        return archive

    def test_unpacks_legitimate_dataset_without_network(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = self.make_zip(root, ["MH_01_easy/mav0/imu0/data.csv",
                                           "MH_01_easy/mav0/cam0/data.csv"])
            result = fetch.unpack_zip(archive, root / "out")
            self.assertEqual(result["files"], 2)
            self.assertTrue((root/"out"/"MH_01_easy"/"mav0"/"imu0"/"data.csv").is_file())

    def test_blocks_zip_traversal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = self.make_zip(root, ["../secrets.txt"])
            with self.assertRaisesRegex(ValueError,"Unsafe path"):
                fetch.unpack_zip(archive, root/"out")
            self.assertFalse((root/"secrets.txt").exists())

    def test_blocks_symlink_and_uncompressed_bomb_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root/"link.zip"
            with zipfile.ZipFile(archive,"w") as zf:
                item = zipfile.ZipInfo("link")
                item.create_system = 3
                item.external_attr = (stat.S_IFLNK | 0o777) << 16
                zf.writestr(item, b"../../unsafe")
            with self.assertRaisesRegex(ValueError,"symlinks"):
                fetch.unpack_zip(archive, root/"out")
            archive = self.make_zip(root, ["regular.dat"])
            with self.assertRaisesRegex(ValueError,"uncompressed size"):
                fetch.unpack_zip(archive,root/"limit", max_bytes=2)

    def test_rejects_missing_checksum_and_http_links(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/"archive.zip"
            with self.assertRaisesRegex(ValueError,"HTTPS"):
                fetch.checked_download("http://example.com/a.zip","0"*64,path,1)
            with self.assertRaisesRegex(ValueError,"SHA256"):
                fetch.checked_download("https://example.com/a.zip","invalid",path,1)


if __name__ == "__main__":
    unittest.main()
