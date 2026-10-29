#!/usr/bin/env python3
"""Static compatibility audit for TikTok Mint against a TikTok APK.

Scans only DEX prototype indexes relevant to known TikTok Mint hook points. It does
not decompile or modify the APK and does not claim successful runtime support.
"""
from __future__ import annotations
import argparse, hashlib, json, os, re, struct, sys
from typing import Dict, List, Set, Tuple

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ROOT)
from kardinebi.apkzip import Apk  # noqa: E402
from kardinebi.axml import Axml  # noqa: E402
from kardinebi import dexpatch  # noqa: E402
from kardinebi.manifest import application_class, min_sdk, package_name, version_name  # noqa: E402

BASELINE_VERSION = "47.2.41"
EXPECTED_PACKAGE = "com.zhiliaoapp.musically"

class DexIndex:
    def __init__(self, data: bytes):
        self.data = data
        self.str_count, self.str_off = struct.unpack_from('<II', data, 56)
        self.type_count, self.type_off = struct.unpack_from('<II', data, 64)
        self.proto_count, self.proto_off = struct.unpack_from('<II', data, 72)
        self.method_count, self.method_off = struct.unpack_from('<II', data, 88)
        self.class_count, self.class_off = struct.unpack_from('<II', data, 96)
        self.strings: Dict[int, str] = {}
        self.types: Dict[int, str] = {}
        self.protos: Dict[int, str] = {}
        self.method_rows: Dict[int, Tuple[str, str, str, int]] = {}
        self.defined: Set[str] = set()
        self.class_types: Set[str] = set()

    def uleb(self, at: int) -> Tuple[int, int]:
        value = shift = 0
        while True:
            byte = self.data[at]; at += 1
            value |= (byte & 0x7f) << shift
            if byte < 0x80: return value, at
            shift += 7

    def string(self, idx: int) -> str:
        if idx not in self.strings:
            at = struct.unpack_from('<I', self.data, self.str_off + 4 * idx)[0]
            _size, at = self.uleb(at)
            end = self.data.find(b'\0', at)
            self.strings[idx] = self.data[at:end].decode('utf-8', 'replace')
        return self.strings[idx]

    def typ(self, idx: int) -> str:
        if idx not in self.types:
            self.types[idx] = self.string(struct.unpack_from('<I', self.data, self.type_off + 4 * idx)[0])
        return self.types[idx]

    def proto(self, idx: int) -> str:
        if idx not in self.protos:
            _shorty, ret, off = struct.unpack_from('<III', self.data, self.proto_off + 12 * idx)
            args = ''
            if off:
                count = struct.unpack_from('<I', self.data, off)[0]
                args = ''.join(self.typ(struct.unpack_from('<H', self.data, off + 4 + 2 * i)[0]) for i in range(count))
            self.protos[idx] = '(' + args + ')' + self.typ(ret)
        return self.protos[idx]

    def method(self, idx: int) -> Tuple[str, str, str, int]:
        if idx not in self.method_rows:
            owner, proto_idx, name_idx = struct.unpack_from('<HHI', self.data, self.method_off + 8 * idx)
            self.method_rows[idx] = (self.typ(owner), self.string(name_idx), self.proto(proto_idx), proto_idx)
        return self.method_rows[idx]

    def scan(self, wanted_descriptors: Set[str], relevant_names: Set[str]) -> Tuple[Set[str], Set[Tuple[str, str, str]], Set[str]]:
        # DEX defines signatures in class_data; method_ids also includes references.
        wanted_proto_ids = {i for i in range(self.proto_count) if self.proto(i) in wanted_descriptors}
        refs: Set[Tuple[str, str, str]] = set()
        # Only construct full owner/name strings for method ids with a relevant
        # descriptor. This avoids decoding the whole multi-hundred-thousand row table.
        relevant_method_ids: Set[int] = set()
        for idx in range(self.method_count):
            owner, proto_idx, name_idx = struct.unpack_from('<HHI', self.data, self.method_off + 8 * idx)
            if proto_idx in wanted_proto_ids:
                name = self.string(name_idx)
                if name in relevant_names or self.proto(proto_idx) in wanted_descriptors:
                    refs.add((self.typ(owner), name, self.proto(proto_idx)))
                    relevant_method_ids.add(idx)
        # Class type names are sufficient for existence checks; method bodies
        # are intentionally not parsed in this static audit.
        for i in range(self.class_count):
            type_idx = struct.unpack_from('<I', self.data, self.class_off + 32 * i)[0]
            self.class_types.add(self.typ(type_idx))
            class_data = struct.unpack_from('<I', self.data, self.class_off + 32 * i + 24)[0]
            if not class_data: continue
            pos = class_data
            static_fields, pos = self.uleb(pos); instance_fields, pos = self.uleb(pos)
            direct_methods, pos = self.uleb(pos); virtual_methods, pos = self.uleb(pos)
            for _ in range(static_fields + instance_fields):
                _, pos = self.uleb(pos); _, pos = self.uleb(pos)
            for method_count in (direct_methods, virtual_methods):
                method_idx = 0
                for step in range(method_count):
                    diff, pos = self.uleb(pos); _flags, pos = self.uleb(pos); _code, pos = self.uleb(pos)
                    method_idx = diff if step == 0 else method_idx + diff
                    # Read the proto index directly first: most methods in the
                    # APK have no relation to this audit and need no decoding.
                    _owner_idx, proto_idx, _name_idx = struct.unpack_from(
                        '<HHI', self.data, self.method_off + 8 * method_idx)
                    if proto_idx in wanted_proto_ids:
                        _owner, _name, _descriptor, _ = self.method(method_idx)
                        self.defined.add('%s->%s%s' % (_owner, _name, _descriptor))
        return self.defined, refs, self.class_types


def audit(path: str) -> dict:
    sha = hashlib.sha256()
    with open(path, 'rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): sha.update(block)
    apk = Apk(path); names = apk.names()
    missing = sorted({'AndroidManifest.xml', 'resources.arsc', 'classes.dex'} - set(names))
    if missing: raise ValueError('not a standalone APK; missing ' + ', '.join(missing))
    manifest = Axml.parse(apk.read('AndroidManifest.xml'))
    package, version, api, app = package_name(manifest), version_name(manifest), min_sdk(manifest), application_class(manifest)
    dex_names = sorted(n for n in names if re.fullmatch(r'classes(?:\d+)?\.dex', n))
    dexes = {n: apk.read(n) for n in dex_names}

    def desc(s): return s
    expected_methods = {
        ('LY/ATListenerS452S0100000_16;', 'onTouch$8', '(LY/ATListenerS452S0100000_16;Landroid/view/View;Landroid/view/MotionEvent;)Z'),
        (dexpatch.SPEED_EDGE_CLASS, 'onTouch', '(Landroid/view/View;Landroid/view/MotionEvent;)Z'),
        (dexpatch.SETTINGS_ROWS, 'LIZ', '(LX/0MUl;ZZLX/0Lx7;I)V'),
        (dexpatch.PLAYER_CONFIG, 'isForceHdrOff', '()Z'),
    }
    want_desc = {sig for _, _, sig in expected_methods}
    # Lottie owners are obfuscated and move; the method descriptor is stable.
    want_desc.add('(Ljava/lang/String;)V')
    relevant_names = {name for _, name, _ in expected_methods}
    relevant_names.add('setAnimation')
    for _label, signature, _ours, _target in dexpatch.DISCOVERED_STATICS + dexpatch.DISCOVERED_VIRTUALS:
        want_desc.add(signature)
    for name, original, _replacement, _target in dexpatch.WILD_SOURCES:
        if name in ('RQ1', 'rP', 'DD2', 'vQ1'):
            want_desc.add(original)
            relevant_names.add(name)
    for label, descriptor, _ours, _target in dexpatch.DISCOVERED_STATICS + dexpatch.DISCOVERED_VIRTUALS:
        relevant_names.add('LIZ' if label == 'comment sticker tapped' else 'LJ')
    all_definitions: Set[str] = set()
    all_refs: Set[Tuple[str, str, str]] = set()
    all_classes: Set[str] = set()
    for name, data in dexes.items():
        di = DexIndex(data)
        definitions, refs, classes = di.scan(want_desc, relevant_names)
        all_definitions.update(definitions); all_refs.update(refs); all_classes.update(classes)

    checks: List[dict] = []
    def add(label, status, detail): checks.append({'label': label, 'status': status, 'detail': detail})
    add('APK package', 'pass' if package == EXPECTED_PACKAGE else 'fail', package)
    add('Source baseline', 'pass' if version == BASELINE_VERSION else 'warn',
        'APK is %s; TikTok Mint 2.0.5 source anchors target %s' % (version, BASELINE_VERSION))
    add('Android minSdk', 'pass' if api >= 23 else 'warn', str(api))
    add('Application', 'info', str(app))
    add('DEX layout', 'info', '%d DEX files' % len(dex_names))

    checks_exact = [
        ('Hold-to-speed feed listener', 'LY/ATListenerS452S0100000_16;', 'onTouch$8', '(LY/ATListenerS452S0100000_16;Landroid/view/View;Landroid/view/MotionEvent;)Z'),
        ('Hold-to-speed edge listener', dexpatch.SPEED_EDGE_CLASS, 'onTouch', '(Landroid/view/View;Landroid/view/MotionEvent;)Z'),
        ('Native settings-row anchor', dexpatch.SETTINGS_ROWS, 'LIZ', '(LX/0MUl;ZZLX/0Lx7;I)V'),
        ('HDR preference hook', dexpatch.PLAYER_CONFIG, 'isForceHdrOff', '()Z'),
    ]
    for label, owner, name, signature in checks_exact:
        key = '%s->%s%s' % (owner, name, signature)
        ok = key in all_definitions
        stat = 'pass' if ok else ('fail' if label != 'HDR preference hook' else 'warn')
        add(label, stat, key + (' is defined' if ok else ' is not defined in this APK'))

    feed_markers = (b'tt_feed_like_tap_tux_lottie.json',
                    b'tt_feed_like_tap_quick_tux_lottie.json',
                    b'feed_fill_like/feed_fill_like_add_lottie.json')
    has_feed_marker = any(any(marker in data for marker in feed_markers)
                          for data in dexes.values())
    has_lottie_call = any(name == 'setAnimation' and descriptor == '(Ljava/lang/String;)V'
                          for _owner, name, descriptor in all_refs)
    add('Feed-heart Lottie adaptation', 'pass' if has_feed_marker and has_lottie_call else 'warn',
        'stable feed-like asset marker and setAnimation(String) reference found; dynamic owner matching is available'
        if has_feed_marker and has_lottie_call else
        'feed marker or setAnimation(String) reference missing; dynamic Lottie hook must be disabled')

    for label, signature, _ours, _target in dexpatch.DISCOVERED_STATICS + dexpatch.DISCOVERED_VIRTUALS:
        matches = sorted({(owner, name) for owner, name, sig in all_refs if sig == signature})
        if len(matches) == 1:
            add('Structural hook: ' + label, 'pass', 'unique match: %s->%s' % matches[0])
        else:
            add('Structural hook: ' + label, 'fail', '%d matching method references; not safe to guess' % len(matches))

    old_sticker_methods = {name: original for name, original, _replacement, _target in dexpatch.WILD_SOURCES
                           if name in ('RQ1', 'rP', 'DD2', 'vQ1')}
    for name, signature in old_sticker_methods.items():
        exact = sorted(k for k in all_definitions if k.endswith('->%s%s' % (name, signature)))
        add('Sticker helper ' + name, 'pass' if exact else 'warn',
            ('exact baseline descriptor found: ' + ', '.join(exact)) if exact else
            'old obfuscated method/signature absent; remapping remains necessary')

    component = 'Landroidx/activity/ComponentActivity;' in all_classes
    add('Activity lifecycle class', 'pass' if component else 'warn',
        'ComponentActivity ' + ('is present' if component else 'was not found'))
    loader = any(b'setProgressBarInfo' in data for data in dexes.values())
    add('Loading-view marker', 'pass' if loader else 'warn',
        'setProgressBarInfo ' + ('is present' if loader else 'was not found'))
    settings_group = dexpatch.SETTINGS_GROUP in all_classes
    add('Native settings group', 'pass' if settings_group else 'warn',
        'old group class ' + ('is present' if settings_group else 'is missing; semantic rediscovery needed'))

    appearance_candidate = (package == EXPECTED_PACKAGE and api >= 23
                            and has_feed_marker and has_lottie_call and loader)
    result = {
        'file': os.path.basename(path), 'size_bytes': os.path.getsize(path),
        'sha256': sha.hexdigest(), 'package': package, 'version': version,
        'baseline_version': BASELINE_VERSION, 'min_sdk': api, 'application': app,
        'dex_count': len(dex_names), 'checks': checks,
        'full_build_ready': (package == EXPECTED_PACKAGE and version == BASELINE_VERSION
                             and not any(x['status'] == 'fail' for x in checks)),
        'appearance_profile_candidate': appearance_candidate,
    }
    return result


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description='Audit a TikTok APK against TikTok Mint’s current hook anchors.')
    parser.add_argument('apk'); parser.add_argument('--json', dest='json_path')
    args = parser.parse_args(argv); path = os.path.abspath(args.apk)
    if not os.path.isfile(path):
        print('ERROR: file not found: %s' % path, file=sys.stderr); return 2
    try: result = audit(path)
    except Exception as error:
        print('ERROR: cannot audit APK: %s' % error, file=sys.stderr); return 2
    print('File:      %s' % result['file'])
    print('Size:      %s bytes' % format(result['size_bytes'], ','))
    print('SHA-256:   %s' % result['sha256'])
    print('Package:   %s' % result['package'])
    print('Version:   %s (source baseline %s)' % (result['version'], result['baseline_version']))
    print('minSdk:    %s' % result['min_sdk'])
    print('DEX files: %s' % result['dex_count'])
    print('\nHook audit:')
    glyph = {'pass':'[OK]', 'fail':'[MISSING]', 'warn':'[CHECK]', 'info':'[INFO]'}
    for item in result['checks']:
        print('%s %-30s %s' % (glyph.get(item['status'], '[?]'), item['label'], item['detail']))
    print('\nAppearance-only profile candidate: %s' % ('YES — expected structural markers found' if result['appearance_profile_candidate'] else 'CHECK — some expected markers are missing'))
    print('Static full-build readiness: %s' % ('YES — pending an actual build test' if result['full_build_ready'] else 'NO — source adaptation is required'))
    if args.json_path:
        with open(args.json_path, 'w', encoding='utf-8') as handle:
            json.dump(result, handle, indent=2, ensure_ascii=False); handle.write('\n')
        print('JSON report: %s' % os.path.abspath(args.json_path))
    return 0 if result['full_build_ready'] else 1

if __name__ == '__main__':
    raise SystemExit(main())
