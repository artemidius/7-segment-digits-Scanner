import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from assets import checked_bytes, download, read_model


class AssetIntegrityTest(unittest.TestCase):
    def test_rejects_changed_model_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'detector.tflite').write_bytes(b'changed')
            (root / 'detector.json').write_text(json.dumps({'model_sha256': hashlib.sha256(b'original').hexdigest()}))
            with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
                read_model(root)

    def test_cached_download_is_verified_without_network(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / 'asset'
            target.write_bytes(b'known')
            digest = hashlib.sha256(b'known').hexdigest()
            with patch('assets.urllib.request.urlopen') as request:
                self.assertEqual(b'known', download('https://unused.invalid', digest, target))
                request.assert_not_called()
            target.write_bytes(b'corrupt')
            with self.assertRaises(ValueError):
                download('https://unused.invalid', digest, target)

    def test_failed_download_does_not_write_unverified_file(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / 'asset'
            with patch('assets.urllib.request.urlopen') as request:
                request.return_value.__enter__.return_value.read.return_value = b'wrong'
                with self.assertRaises(ValueError):
                    download('https://unused.invalid', hashlib.sha256(b'right').hexdigest(), target)
            self.assertFalse(target.exists())
            self.assertFalse(target.with_suffix('.tmp').exists())

    def test_checked_bytes_returns_original_data(self):
        data = b'verified'
        self.assertIs(data, checked_bytes(data, hashlib.sha256(data).hexdigest(), 'fixture'))


if __name__ == '__main__':
    unittest.main()
