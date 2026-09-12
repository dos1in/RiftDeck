"""Convert Android 12 profman text output into exact application profile rules."""
import argparse
import re
from pathlib import Path

PRIMITIVES = dict(zip(('void', 'boolean', 'byte', 'char', 'short', 'int', 'long', 'float', 'double'), 'VZBCSIJFD'))
SECTIONS = {'hot methods:': 'H', 'startup methods:': 'S', 'post startup methods:': 'P', 'classes:': 'C'}


def descriptor(name):
    name = name.strip()
    if name.endswith('[]'):
        return '[' + descriptor(name[:-2])
    if name in PRIMITIVES:
        return PRIMITIVES[name]
    if not re.fullmatch(r'[\w.$-]+', name):
        raise ValueError('Unsupported type: ' + name)
    return 'L' + name.replace('.', '/') + ';'


def convert(lines):
    section = None
    rules = {}
    for raw in lines:
        line = raw.strip()
        if line in SECTIONS:
            section = SECTIONS[line]
            continue
        if not raw.startswith('\t\t'):
            continue
        if section == 'C':
            if line.startswith('com.riftdeck.'):
                rules.setdefault(descriptor(line), set())
            continue
        if section not in ('H', 'S', 'P'):
            continue
        match = re.match(r'([^ ]+) (com\.riftdeck\.[^ (]+)\(([^)]*)\)', line)
        if not match:
            if re.match(r'[^ ]+ com\.riftdeck\.', line):
                raise ValueError('Unrecognized method: ' + line)
            continue
        result, qualified, arguments = match.groups()
        owner, method = qualified.rsplit('.', 1)
        args = ''.join(descriptor(arg) for arg in arguments.split(',')) if arguments else ''
        rule = descriptor(owner) + '->' + method + '(' + args + ')' + descriptor(result)
        rules.setdefault(rule, set()).add(section)
    if not rules:
        raise ValueError('No application rules found')
    return '\n'.join(''.join(flag for flag in 'HSP' if flag in flags) + rule
                     for rule, flags in sorted(rules.items())) + '\n'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dump', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.write_text(convert(args.dump.read_text().splitlines()))
