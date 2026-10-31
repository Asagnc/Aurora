"""Record license declarations from the exact public POMs; never follows external entities or URLs."""
from pathlib import Path
import xml.etree.ElementTree as xml


def declarations(directory, report):
    poms = {}
    for row in report['metadata']:
        if not row['path'].endswith('.pom'):
            continue
        data = (directory / row['path']).read_bytes()
        if len(data) > 4 * 1024 ** 2:
            raise ValueError('Public POM exceeds license metadata budget')
        tree = xml.fromstring(data)
        for element in tree.iter():
            element.tag = element.tag.rsplit('}', 1)[-1]
        poms.setdefault(row['coordinate'], []).append((tree, row))

    def inherited(coordinate, seen=()):
        if coordinate in seen or len(seen) >= 32:
            return [], 'parent_cycle_or_budget'
        if coordinate not in poms:
            return [], 'parent_pom_missing'
        result, errors = [], []
        for tree, row in poms[coordinate]:
            own = [{'name': entry.findtext('name', '').strip(), 'url': entry.findtext('url', '').strip(),
                    'fromCoordinate': coordinate, 'pomPath': row['path']} for entry in tree.findall('licenses/license')]
            own = [entry for entry in own if entry['name'] or entry['url']]
            if own:
                result += own
                continue
            parent = tree.find('parent')
            if parent is None:
                errors.append('no_license_or_parent')
                continue
            parts = [parent.findtext(key, '').strip() for key in ['groupId', 'artifactId', 'version']]
            if not all(parts) or any('${' in value for value in parts):
                errors.append('unresolved_parent')
                continue
            found, error = inherited(':'.join(parts), (*seen, coordinate))
            result += found
            if error:
                errors.append(error)
        unique = [dict(items) for items in {tuple(sorted(row.items())) for row in result}]
        return unique, ','.join(sorted(set(errors))) if not unique else ''

    rows = []
    for coordinate in sorted(report['binaries']):
        found, error = inherited(coordinate)
        rows.append({'coordinate': coordinate, 'licenses': sorted(found, key=lambda row: (row['name'], row['url'])),
                     'unresolved': error or None})
    report['licenseDeclarations'] = rows
    report['missingLicenseDeclarations'] = [row['coordinate'] for row in rows if not row['licenses']]
    lines = ['Aurora public Android build dependency baseline', 'Cache SHA256: ' + report['cachePin']['sha256'],
             'Recorded public POM license declarations; original metadata, embedded notices and coordinate/version sources accompany this file.', '']
    for row in rows:
        lines.append(row['coordinate'])
        for item in row['licenses']:
            lines.append('  ' + item['name'] + ' ' + item['url'] + ' (POM ' + item['fromCoordinate'] + ')')
        if row['unresolved']:
            lines.append('  Unresolved declaration: ' + row['unresolved'])
    (directory / 'ATTRIBUTIONS.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
