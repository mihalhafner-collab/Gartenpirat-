"""Presentation-only German search/order rules; never changes stored values or IDs."""
import re
import unicodedata


def fold(value):
    value = ''.join(c for c in unicodedata.normalize('NFKD', str(value or ''))
                    if not unicodedata.combining(c)).casefold()
    return value.replace('ae', 'a').replace('oe', 'o').replace('ue', 'u')


def matches(query, values):
    values = list(values)
    text = fold(' '.join(str(value or '') for value in values))
    if all(word in text for word in fold(query).split()):
        return True
    raw = str(query or '').strip()
    digits = re.sub(r'\D', '', raw)
    return (len(digits) >= 4 and re.fullmatch(r'[+0-9\s()./\-]+', raw) is not None
            and any(digits in re.sub(r'\D', '', str(value or '')) for value in values))


def active(value):
    return value not in (False, 0, '0', 'false')


def natural_key(value):
    return tuple((0, int(part)) if part.isdecimal() else (1, part)
                 for part in re.findall(r'[0-9]+|[^0-9]+', fold(value)))


def customers(rows):
    return sorted(rows, key=lambda row: (not active(row.get('active', 1)),
                  natural_key(row.get('name')), natural_key(row.get('company')),
                  str(row.get('id') or '')))
