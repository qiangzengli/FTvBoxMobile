#!/usr/bin/env python3
"""Restore the encrypted release key and Gradle signing configuration."""

import argparse
import base64
import getpass
import json
import os
from pathlib import Path

from cryptography.fernet import Fernet, InvalidToken
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
from cryptography.hazmat.primitives.serialization.pkcs12 import load_key_and_certificates


def write_private(path, content):
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as output:
        output.write(content)


def main():
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup", type=Path, default=root / "signing/release-signing.enc.json")
    parser.add_argument("--passphrase-file", type=Path, help="Otherwise prompt without echoing")
    parser.add_argument("--key-dir", type=Path, default=Path.home() / ".android/keystores/fbox-mobile")
    parser.add_argument("--config", type=Path, default=root / "release-signing.properties")
    args = parser.parse_args()

    key_path = args.key_dir.resolve() / "fbox-mobile-release.keystore"
    if key_path.exists() or args.config.exists():
        parser.error("Key or configuration already exists; use another destination to avoid overwriting it.")

    envelope = json.loads(args.backup.read_text())
    if envelope["format"] != "fbox-release-signing-fernet-scrypt-v1":
        parser.error("Unsupported backup format")
    passphrase = (args.passphrase_file.read_text().strip() if args.passphrase_file
                  else getpass.getpass("Signing backup passphrase: "))
    salt = base64.b64decode(envelope["salt"], validate=True)
    key = Scrypt(salt=salt, length=32, n=32768, r=8, p=1).derive(passphrase.encode())
    try:
        payload = json.loads(Fernet(base64.urlsafe_b64encode(key)).decrypt(envelope["token"].encode()))
    except InvalidToken:
        parser.error("Incorrect passphrase or damaged backup; no files were written.")

    keystore = base64.b64decode(payload["keystore"], validate=True)
    private_key, certificate, _ = load_key_and_certificates(keystore, payload["storePassword"].encode())
    if private_key is None or certificate is None:
        parser.error("Backup does not contain a signing key and certificate")
    for field in ("storePassword", "keyAlias", "keyPassword"):
        if any(char in payload[field] for char in "\r\n\\"):
            parser.error("Signing property contains unsupported characters")

    args.key_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    config = (f"storeFile={key_path.as_posix()}\n"
              f"storePassword={payload['storePassword']}\n"
              f"keyAlias={payload['keyAlias']}\n"
              f"keyPassword={payload['keyPassword']}\n")
    write_private(key_path, keystore)
    write_private(args.config, config.encode())
    print(f"Restored key: {key_path}")
    print(f"Restored Gradle configuration: {args.config}")
    print(f"Certificate SHA-256: {certificate.fingerprint(hashes.SHA256()).hex()}")


if __name__ == "__main__":
    main()
