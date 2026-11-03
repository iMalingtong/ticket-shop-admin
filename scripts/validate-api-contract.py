#!/usr/bin/env python3
"""Validate local-only OpenAPI, source endpoint coverage and synthetic examples; no network resolution."""
from pathlib import Path
import re
import sys
import warnings
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'runtime/python'))
import yaml
from openapi_spec_validator import validate_spec
from jsonschema import Draft4Validator, FormatChecker
with warnings.catch_warnings():
    warnings.simplefilter('ignore', DeprecationWarning)
    from jsonschema import RefResolver

contract = yaml.safe_load((ROOT/'docs/api/openapi.yaml').read_text())

def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)

assert all(v['$ref'].startswith('#/') for v in walk(contract) if '$ref' in v), 'Only local references permitted'
validate_spec(contract)
actual = {(method.upper(), path) for path, methods in contract['paths'].items() for method in methods}
source = (ROOT/'docs/DEVELOPMENT_SPEC.md').read_text()
realm = None
expected = set()
for line in source.splitlines():
    if line.startswith('### 8.1'): realm = 'client'
    elif line.startswith(('### 8.2', '### 8.3', '### 8.4')): realm = 'admin'
    elif line.startswith('## 9.'): break
    if realm:
        match = re.match(r'\| (GET|POST|PUT|PATCH|DELETE) `([^`]+)`', line)
        if match: expected.add((match[1], f'/api/v1/{realm}{match[2]}'))
assert expected == actual, f'Endpoint mismatch: missing={expected-actual}, extra={actual-expected}'
operation_ids = []
for path, methods in contract['paths'].items():
    for method, operation in methods.items():
        operation_ids.append(operation['operationId'])
        assert 'security' in operation and 'x-permissions' in operation
        parameters = {p.get('$ref','') for p in operation['parameters']}
        if path.startswith('/api/v1/admin/') and method != 'get':
            assert '#/components/parameters/Csrf' in parameters
            assert '#/components/parameters/Origin' in parameters
            if '/auth/' not in path: assert '#/components/parameters/Idempotency' in parameters
        if '{id}' in path: assert '#/components/parameters/Id' in parameters
assert len(operation_ids) == len(set(operation_ids)), 'Duplicate operationId'
backend_codes = (ROOT/'backend/src/main/java/com/ticketshop/admin/common/api/ErrorCode.java').read_text()
actual_codes = {code:int(status) for code,status in re.findall(r'([A-Z][A-Z_]+)\((\d{3})\)', backend_codes)}
expected_codes = {code:int(name.removeprefix('ErrorCode')) for name,schema in contract['components']['schemas'].items() if re.fullmatch(r'ErrorCode[0-9]{3}', name) for code in schema['enum']}
assert actual_codes == expected_codes, 'Java error-code status mapping drift'


def json_schema(value):
    if isinstance(value, list): return [json_schema(v) for v in value]
    if not isinstance(value, dict): return value
    result = {k: json_schema(v) for k,v in value.items() if k != 'nullable'}
    if value.get('nullable') and 'type' in value: result['type'] = [value['type'], 'null']
    return result
normalized = json_schema(contract)
resolver = RefResolver.from_schema(normalized)
examples = 0
for name, schema in normalized['components']['schemas'].items():
    if 'example' in schema:
        Draft4Validator(schema, resolver=resolver, format_checker=FormatChecker()).validate(schema['example'])
        examples += 1
# Explicitly check request strictness and secret replay shape, beyond spec syntax.
for name in ('CreateCustomerRequest','UpdateCustomerRequest','ResetPasswordRequest','CreateAdministratorRequest','UpdatePolicyRequest'):
    assert normalized['components']['schemas'][name]['additionalProperties'] is False
assert 'temporaryPassword' not in normalized['components']['schemas']['SecretUnavailable']['properties']
assert 'token' not in normalized['components']['schemas']['AdminNormalLogin']['properties']
print(f'OpenAPI validated: {len(actual)} operations, {len(operation_ids)} unique IDs, {examples} synthetic examples; complete source coverage')
