import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const root = process.cwd();
const corpus = JSON.parse(await readFile(path.join(root, 'test/multimodal-evaluation-corpus-v1.json')));
const annotations = JSON.parse(await readFile(path.join(root, 'test/multimodal-evaluation-annotations-v1.json')));
if (annotations.corpusVersion !== corpus.corpusVersion) throw new Error('Annotation/corpus version mismatch.');

const assets = new Map(annotations.assets.map((asset) => [asset.id, asset]));
const duplicateAssets = annotations.assets.length - assets.size;
const invalidFacts = annotations.facts.filter((fact) => {
  const asset = assets.get(fact.assetId);
  return !asset || asset.paperId !== fact.paperId || !Number.isInteger(asset.page) || asset.page < 1;
});
if (duplicateAssets || invalidFacts.length) throw new Error(`Invalid annotations: duplicateAssets=${duplicateAssets}, invalidFacts=${invalidFacts.length}`);

const corpusHashes = [];
for (const paper of corpus.papers) {
  const buffer = await readFile(path.join(root, paper.relativePath));
  const actual = createHash('sha256').update(buffer).digest('hex');
  if (actual !== paper.sha256) throw new Error(`PDF hash mismatch for paper ${paper.paperId}`);
  corpusHashes.push({ paperId: paper.paperId, sha256: actual });
}

const output = {
  evaluationVersion: 'multimodal-baseline-text-v1',
  evaluatedAt: new Date().toISOString(),
  corpusVersion: corpus.corpusVersion,
  annotationSchemaVersion: annotations.schemaVersion,
  evaluator: 'deterministic baseline validator',
  corpusHashes,
  counts: { papers: corpus.papers.length, annotatedAssets: annotations.assets.length, annotatedFacts: annotations.facts.length },
  baseline: {
    system: 'existing PDFBox full-text, chunk, profile and section-summary chain',
    assetDetection: { detected: 0, expected: annotations.assets.length, recall: 0, precision: null, reason: 'No page-level asset records exist in the current schema.' },
    sourceLocalization: { localizedFacts: 0, expected: annotations.facts.length, accuracy: 0, reason: 'Current PaperChunk pageNumber is not populated by the full-text parser.' },
    reproductionFactRecall: { matched: 0, expected: annotations.facts.length, recall: 0, reason: 'No reproducible atomic-fact store or source-kind/page matching exists yet.' },
    provenanceCompleteness: { supportedFacts: 0, evaluatedFacts: annotations.facts.length, rate: 0 },
    unsupportedFactRate: { unsupportedFacts: 0, rate: 0, note: 'No newly generated multimodal facts are asserted by this baseline.' }
  },
  limitations: ['This is a strict structural baseline, not a claim that the PDFs contain no textual evidence.', 'Do not compare precision where the current chain produces no assets.', 'The result is intentionally retained unchanged for later stage comparisons.']
};
const outputFile = process.env.MULTIMODAL_EVAL_OUTPUT_FILE || 'test/results/multimodal-evaluation-baseline-text-v1-2026-07-27.json';
await writeFile(path.join(root, outputFile), `${JSON.stringify(output, null, 2)}\n`);
console.log(JSON.stringify(output, null, 2));
