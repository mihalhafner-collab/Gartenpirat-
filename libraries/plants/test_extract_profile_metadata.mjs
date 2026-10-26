import { test } from 'node:test';
import assert from 'node:assert/strict';
import { compactFullProfile } from './extract_profile_metadata.mjs';

test('Original DOM preserves acidity, identity, and image-level credit association', () => {
  const p = compactFullProfile({url: 'https://example.invalid/qa-plant', text: `
    <h1>QA plant <span>Play pronunciation</span></h1>
    <dl><dt>Soil pH:</dt><dd><span>Acid (&lt;6.0)</span></dd></dl>
    <img data-caption="QA flower" data-attrib="QA Artist"
      data-license="&lt;a href=&quot;https://creativecommons.org/licenses/by/4.0/&quot;&gt;CC BY 4.0&lt;/a&gt;"
      data-downloadurl="https://example.invalid/qa.jpg?signature=qa_temporary">
    <img data-caption="QA restricted" data-attrib="QA Artist"
      data-license="&lt;a href=&quot;https://creativecommons.org/licenses/by-nc/4.0/&quot;&gt;CC BY-NC 4.0&lt;/a&gt;"
      data-downloadurl="https://example.invalid/qa-nc.jpg">`});
  assert.deepEqual(p.identityHeadings, ['QA plant']);
  assert.deepEqual(p.facts['Soil pH'], ['Acid (<6.0)']);
  assert.equal(p.figures[0].author, 'QA Artist');
  assert.equal(p.figures[0].licenseId, 'CC-BY-4.0');
  assert.equal(p.figures[0].url, 'https://example.invalid/qa.jpg');
  assert.equal(p.figures[0].association, 'original_image_data_attributes');
  assert.equal(p.figures[1].eligible, false);
});

test('RHS source fields retain their own aliases and harmful warnings', () => {
  const p = compactFullProfile({url:'https://www.rhs.org.uk/qa-plant', text: `
    <h1>QA plant</h1><h2>Position</h2>Full sun<h2>Soil Types</h2>Clay Loam
    <h2>Other</h2><dl><dt>Moisture</dt><dd>Moist but well–drained or Well–drained</dd>
    <dt>Potentially harmful</dt><dd>QA warning; wear gloves</dd></dl>`});
  assert.deepEqual(p.facts.Light, ['Full sun']);
  assert.deepEqual(p.facts['Soil Drainage'], ['Moist but well–drained', 'Well–drained']);
  assert.equal(p.fieldAliases['Soil Drainage'], 'Moisture');
  assert.deepEqual(p.facts['Potentially harmful'], ['QA warning; wear gloves']);
});
