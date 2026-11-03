#!/usr/bin/env python3
"""Explicit local development bootstrap/migration. Never prints or stores credentials."""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import uuid
import socket
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'runtime/python'))
DB_URL = 'jdbc:mysql://127.0.0.1:3306/ticket_shop_admin?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8'


def main():
    parser = argparse.ArgumentParser(description='迁移专用开发库并启动服务；仅限本机指定compose')
    parser.add_argument('--compose', required=True, type=Path, help='明确授权的单个compose；不读取关联配置文件')
    parser.add_argument('--migrate-only', action='store_true', help='只执行Flyway；不轮换运行账号密码、不启动HTTP')
    parser.add_argument('--auth-config', type=Path, help='明确授权的本项目新建开发配置；不自动搜索凭据')
    parser.add_argument('--bootstrap-admin', action='store_true', help='离线交互引导管理员，不启动HTTP')
    args = parser.parse_args()
    auth_env = {}
    if args.bootstrap_admin and args.migrate_only:
        sys.exit('引导与仅迁移不能同时执行')
    if not args.migrate_only:
        if args.auth_config is not None:
            expected = ROOT / 'runtime/keys/development-auth.json'
            if args.auth_config.absolute() != expected or args.auth_config.is_symlink() or args.auth_config.parent.is_symlink() or args.auth_config.parent.stat().st_mode & 0o077 or args.auth_config.stat().st_mode & 0o077:
                sys.exit('只接受本次明确授权创建的项目开发配置，文件必须权限600')
            config = json.loads(args.auth_config.read_text())
            mapping = {'privateKey': 'TICKET_SHOP_JWT_PRIVATE_KEY', 'publicKey': 'TICKET_SHOP_JWT_PUBLIC_KEY', 'hmacKey': 'TICKET_SHOP_HMAC_KEY', 'keyId': 'TICKET_SHOP_JWT_KEY_ID'}
            if set(config) != set(mapping) or any(not isinstance(v, str) or not v for v in config.values()):
                sys.exit('开发签名配置结构无效')
            auth_env = {mapping[k]: v for k, v in config.items()}
            del config
        elif any(not os.environ.get(k) for k in ('TICKET_SHOP_JWT_PRIVATE_KEY', 'TICKET_SHOP_JWT_PUBLIC_KEY', 'TICKET_SHOP_HMAC_KEY')):
            sys.exit('需要显式开发签名配置；不自动生成或读取已有密钥')
    try:
        import yaml
    except ImportError:
        sys.exit('缺少PyYAML，请按本机操作手册安装工具依赖')
    if not args.migrate_only:
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', 18080)) == 0:
                sys.exit('18080被占用；不修改运行账号密码，请先停止本项目实例')
    jar = ROOT / 'backend/target/ticket-shop-admin-0.1.0-SNAPSHOT.jar'
    if not jar.is_file():
        sys.exit('请先执行后端verify生成JAR')
    snapshot = ROOT / 'runtime/backend' / (uuid.uuid4().hex + '.jar')
    snapshot.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(jar, snapshot)
    config = yaml.safe_load(args.compose.read_text())
    service = config.get('services', {}).get('mysql', {})
    environment = service.get('environment', {})
    if isinstance(environment, list):
        environment = dict(item.split('=', 1) for item in environment if '=' in item)
    root_password = environment.get('MYSQL_ROOT_PASSWORD')
    if not isinstance(root_password, str) or not root_password or '${' in root_password:
        sys.exit('指定文件未内联可用管理凭据；不读取其他凭据文件')
    if service.get('container_name') != 'mysql' or service.get('ports') != ['3306:3306']:
        sys.exit('服务或端口与已授权环境不符，停止')
    admin_env = {**os.environ, 'MYSQL_PWD': root_password}

    def sql(statement):
        result = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', 'mysql', 'mysql', '-uroot'],
                                input=statement, text=True, env=admin_env, capture_output=True, check=False)
        if result.returncode:
            sys.exit('专用开发库/账号准备失败；错误输出已隐藏，避免泄露凭据')

    migration_password = secrets.token_urlsafe(32)
    sql(f"""CREATE DATABASE IF NOT EXISTS ticket_shop_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'ticket_shop_admin_migrator'@'%' IDENTIFIED BY '{migration_password}';
ALTER USER 'ticket_shop_admin_migrator'@'%' IDENTIFIED BY '{migration_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON ticket_shop_admin.* TO 'ticket_shop_admin_migrator'@'%';
""")
    migration_env = dict(os.environ)
    migration_env.pop('MYSQL_PWD', None)
    migration_env.update({'JENV_VERSION': 'openjdk64-21.0.12', 'SPRING_PROFILES_ACTIVE': 'development',
                          'TICKET_SHOP_DB_URL': DB_URL, 'TICKET_SHOP_MIGRATION_USERNAME': 'ticket_shop_admin_migrator',
                          'TICKET_SHOP_MIGRATION_PASSWORD': migration_password})
    result = subprocess.run(['jenv', 'exec', 'java', '-jar', str(snapshot), '--migrate'],
                            env=migration_env, capture_output=True, text=True, check=False)
    # Only fixed migration summary is public; JDBC exceptions can otherwise contain connection configuration.
    if result.returncode:
        sys.exit('Flyway迁移或校验失败；未启动应用，未自动repair/clean；请在隔离测试环境定位')
    del migration_env, migration_password, result
    print('专用开发库Flyway已迁移并校验至V3。', flush=True)
    if args.migrate_only:
        return
    password = secrets.token_urlsafe(32)
    sql(f"""CREATE USER IF NOT EXISTS 'ticket_shop_admin_app'@'%' IDENTIFIED BY '{password}';
ALTER USER 'ticket_shop_admin_app'@'%' IDENTIFIED BY '{password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON ticket_shop_admin.* TO 'ticket_shop_admin_app'@'%';
""")
    del admin_env, root_password, config, environment, service, sql
    env = dict(os.environ)
    for key in ('MYSQL_PWD', 'TICKET_SHOP_MIGRATION_USERNAME', 'TICKET_SHOP_MIGRATION_PASSWORD'):
        env.pop(key, None)
    env.update(auth_env)
    env.update({'JENV_VERSION': 'openjdk64-21.0.12', 'SPRING_PROFILES_ACTIVE': 'development',
                'TICKET_SHOP_DB_URL': DB_URL, 'TICKET_SHOP_DB_USERNAME': 'ticket_shop_admin_app',
                'TICKET_SHOP_DB_PASSWORD': password, 'TICKET_SHOP_REDIS_HOST': '127.0.0.1', 'TICKET_SHOP_REDIS_PORT': '6379'})
    print('运行账号仅DML，临时密码只传入当前Java进程。', flush=True)
    os.execvpe('jenv', ['jenv', 'exec', 'java', '-jar', str(snapshot)] + (['--bootstrap-admin'] if args.bootstrap_admin else []), env)


if __name__ == '__main__':
    main()
