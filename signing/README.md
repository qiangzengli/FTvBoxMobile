# Release 签名

应用 ID 为 `com.fbox.fbox`，密钥别名为 `fbox-mobile`，密钥格式为 PKCS12，算法为 RSA 3072。

- `release-signing.enc.json`：包含完整签名密钥、密钥别名及两项密码的加密备份。
- `release-certificate.pem`：可公开的签名证书，不含私钥。
- `release-certificate.json`：证书指纹、有效期和算法信息。

备份使用 Fernet 认证加密，口令通过 scrypt（N=32768、r=8、p=1，随机 16 字节盐）派生加密密钥。解密口令单独保存在本机 `~/.android/keystores/fbox-mobile/backup-passphrase.txt`，不提交到公开仓库。请将该口令另行备份到密码管理器；仅有仓库内容不能恢复私钥。

## 在新电脑恢复

需要 Python 3 和 `cryptography`（可在虚拟环境运行 `python3 -m pip install cryptography` 安装）。

```sh
python3 tools/restore_release_signing.py
```

按提示输入备份口令，输入不会回显。也可使用 `--passphrase-file /安全目录/backup-passphrase.txt`。脚本先解密并验证 PKCS12 内容，再以仅当前用户可读写的权限恢复密钥和根目录的 `release-signing.properties`，不会覆盖已有文件。随后按根目录 README 构建 release。

证书 SHA-256：

```text
8ba70010a7fcd0a2d97178fcf5a688573ef788e9b6ee54a87938a337f83b21ce
```

这个独立 release 签名与此前 Android 调试签名不同，不能直接覆盖调试版。更换签名前应备份应用数据。
