#!/usr/bin/env python3
# coding: utf-8
"""Isolated synthetic CLI acceptance only; no shared DB or real credentials."""
import os
import pty
import select
import sys
import time

if os.environ.get('SPRING_PROFILES_ACTIVE') != 'test' or 'ticket_shop_admin_auth_test' not in os.environ.get('TICKET_SHOP_DB_URL', ''):
    raise SystemExit('仅允许专用隔离认证测试库')
jar, java = sys.argv[1:3]
password = 'SyntheticInitialPassword-2026'

def run(expected_success):
    pid, fd = pty.fork()
    if pid == 0:
        os.execv(java, [java, '-jar', jar, '--bootstrap-admin'])
    output = bytearray()
    prompts = [('管理员账号：', 'SyntheticCliAdmin'), ('显示名称：', '合成命令行管理员'), ('初始密码（隐藏输入）：', password), ('再次输入：', password)]
    position = 0
    deadline = time.monotonic() + 30
    status = None
    try:
        while time.monotonic() < deadline:
            if select.select([fd], [], [], .1)[0]:
                try:
                    block = os.read(fd, 8192)
                except OSError:
                    break
                if not block:
                    break
                output.extend(block)
                if position < len(prompts) and prompts[position][0].encode() in output:
                    # Console.readPassword has disabled echo before printing its prompt.
                    os.write(fd, (prompts[position][1] + '\n').encode())
                    position += 1
            done, candidate = os.waitpid(pid, os.WNOHANG)
            if done:
                status = candidate
                break
        if status is None:
            done, candidate = os.waitpid(pid, os.WNOHANG)
            if done:
                status = candidate
            else:
                os.kill(pid, 15)
                _, status = os.waitpid(pid, 0)
                raise AssertionError('CLI超时')
        assert position == 4, '未完成隐藏输入流程'
        assert password.encode() not in output, '合成密码被回显'
        assert (os.waitstatus_to_exitcode(status) == 0) == expected_success, 'CLI退出状态不符合预期'
        if expected_success:
            assert '管理员引导完成'.encode() in output, '未观察到成功提示'
    finally:
        os.close(fd)

run(True)
run(False)
print('离线CLI首次引导成功、重复拒绝、密码未回显。')
