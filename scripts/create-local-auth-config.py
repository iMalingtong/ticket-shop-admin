#!/usr/bin/env python3
# coding: utf-8
"""Explicit opt-in development credentials. Never executed automatically or overwrite existing keys."""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description='显式新建本项目开发签名配置；不覆盖或读取已有凭据')
    parser.add_argument('--create', action='store_true', required=True)
    parser.parse_args()
    directory = ROOT / 'runtime/keys'
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    if directory.is_symlink() or directory.stat().st_mode & 0o077:
        raise SystemExit('密钥目录必须为非符号链接且权限700')
    target = directory / 'development-auth.json'
    if target.exists() or target.is_symlink():
        raise SystemExit('配置已存在；不覆盖、不读取、不自动轮换')
    def openssl(arguments, data=None):
        result = subprocess.run(['openssl', *arguments], input=data, capture_output=True, check=False)
        if result.returncode:
            raise SystemExit('密钥生成失败；错误内容已隐藏')
        return result.stdout
    private_pem = openssl(['genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:3072'])
    private_der = openssl(['pkcs8', '-topk8', '-nocrypt', '-outform', 'DER'], private_pem)
    public_der = openssl(['pkey', '-pubout', '-outform', 'DER'], private_pem)
    value = {'privateKey': base64.b64encode(private_der).decode('ascii'),
             'publicKey': base64.b64encode(public_der).decode('ascii'),
             'hmacKey': base64.b64encode(secrets.token_bytes(32)).decode('ascii'), 'keyId': 'local-development-1'}
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, 'w') as stream:
        json.dump(value, stream)
    print('本项目开发签名配置已新建，目录700/文件600；不适用于生产。')

if __name__ == '__main__':
    main()
