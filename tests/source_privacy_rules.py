"""Typed, consistent synthetic references for a source-only export."""
import ast
import hashlib
import re


def sanitize_snapshot_verifier(data):
    """Remove private import totals only in the exported verifier.

    Duplicate/reference integrity checks remain active. A historical import's
    customer counts and invoice totals are not valid Cloud test expectations.
    """
    source = data.decode('utf-8')
    assignments = [node for node in ast.parse(source).body
                   if isinstance(node, ast.Assign) and len(node.targets) == 1
                   and isinstance(node.targets[0], ast.Name)
                   and node.targets[0].id == 'expected']
    if len(assignments) != 1:
        raise ValueError('Snapshot verifier expectations could not be identified.')
    node = assignments[0]
    expected = ast.literal_eval(node.value)
    snapshot_keys = {'customers', 'invoices', 'paid_invoices', 'open_invoices',
                     'invoice_amount_sum'}
    integrity_keys = {'duplicate_customer_source_ids',
                      'duplicate_invoice_source_ids', 'invoices_without_customer'}
    if not isinstance(expected, dict) or set(expected) != snapshot_keys | integrity_keys:
        raise ValueError('Unexpected verifier rules; manual privacy review required.')
    kept = {key: value for key, value in expected.items() if key in integrity_keys}
    replacement = ('# Cloud copy: structural integrity, not private import totals.\n'
                   + 'expected = ' + repr(kept))
    return source.replace(ast.get_source_segment(source, node), replacement, 1).encode('utf-8')


def fixture_value(value, matched):
    digest = hashlib.sha256(value.encode('utf-8')).hexdigest()[:12]
    if '@' in value:
        result = 'fixture_' + digest + '@example.invalid'
    elif re.fullmatch(r'[+\d\s()/.-]+', value) and len(re.sub(r'\D', '', value)) >= 8:
        result = '+430000' + str(int(digest, 16) % 10**6).zfill(6)
    elif re.fullmatch(r'[A-Z]{2}\s*\d[\d\s]{13,}', value):
        # A synthetic AT IBAN with valid checksum; never a copied bank account.
        bban = '0000000000000000'
        result = 'AT' + str(98 - int(bban + '102900') % 97).zfill(2) + bban
    else:
        whitespace = re.search(r'\s+', matched)
        result = ('Cloudfixture' + whitespace.group() + digest) if whitespace else ('Cloudfixture_' + digest)
    return result.upper() if matched.isupper() else result.lower() if matched.islower() else result


def anonymize(data, markers, suffix):
    # Keep surname/partial-name searches meaningful in anonymized test fixtures.
    # Only ordinary two-part personal names, only inside source string literals;
    # never rewrite enums, variable names, CSS properties or general labels.
    name_tokens = set()
    for value in markers:
        if re.fullmatch(r'[A-ZÄÖÜ][a-zäöüß]{2,}\s+[A-ZÄÖÜ][a-zäöüß]{4,}', value):
            name_tokens.update(part for part in value.split() if part not in {'Hafner'})
    if name_tokens:
        words = re.compile(r'\b(?:' + '|'.join(re.escape(x) for x in sorted(name_tokens, key=len, reverse=True)) + r')\b', re.IGNORECASE)
        literals = rb"(['\"])(?:\\.|(?!\1).)*\1"
        def literal(match):
            text = match.group().decode('utf-8')
            def word(m):
                replacement = 'Cloudname_' + hashlib.sha256(m.group().lower().encode()).hexdigest()[:10]
                return replacement.upper() if m.group().isupper() else replacement
            return words.sub(word, text).encode('utf-8')
        data = re.sub(literals, literal, data)
    for value in sorted(markers, key=len, reverse=True):
        # Preserve case/whitespace duplicate scenarios, not productive values.
        pattern = r'\s+'.join(re.escape(part) for part in value.split())
        if pattern:
            data = re.sub(pattern.encode('utf-8'),
                          lambda m: fixture_value(value, m.group().decode('utf-8')).encode('utf-8'),
                          data, flags=re.IGNORECASE)
    # Test identities retain their valid shape while avoiding real connection IDs.
    quoted = rb"(['\"])(tunnel_[a-z0-9]{32}|org-[A-Za-z0-9]{18,64}|plugin_asdk_app_[a-z0-9]{32})\1"
    def identifier(match):
        quote, value = match.group(1), match.group(2)
        prefix = b'tunnel_' if value.startswith(b'tunnel_') else b'org-' if value.startswith(b'org-') else b'plugin_asdk_app_'
        digest = hashlib.sha256(value).hexdigest()[:32].encode()
        if suffix == '.json':
            # JSON cannot contain an expression. No such ID is expected in the
            # current archive; the mandatory final scan rejects it if encountered.
            return quote + prefix + digest + quote
        return b'(' + quote + prefix + quote + b' + ' + quote + digest + quote + b')'
    return re.sub(quoted, identifier, data)
