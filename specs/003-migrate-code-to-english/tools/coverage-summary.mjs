#!/usr/bin/env node
// Resume a cobertura de linha e de branch de um jacoco.csv (tasks T001, T023, T047…): totais e porcentagens.
//   node coverage-summary.mjs backend/<core>/target/site/jacoco/jacoco.csv
// O primeiro campo do CSV traz o nome do módulo entre aspas e com vírgula; por isso as colunas contam do fim.
import { readFileSync } from 'node:fs';

const file = process.argv[2];
if (!file) {
  console.error('Uso: coverage-summary.mjs <jacoco.csv>');
  process.exit(2);
}
let lineMissed = 0;
let lineCovered = 0;
let branchMissed = 0;
let branchCovered = 0;
for (const row of readFileSync(file, 'utf8').trim().split(/\r?\n/).slice(1)) {
  const columns = row.split(',');
  branchMissed += Number(columns.at(-8));
  branchCovered += Number(columns.at(-7));
  lineMissed += Number(columns.at(-6));
  lineCovered += Number(columns.at(-5));
}
const percent = (covered, missed) => ((100 * covered) / (covered + missed)).toFixed(2);
console.log(`linhas: ${lineCovered}/${lineCovered + lineMissed} = ${percent(lineCovered, lineMissed)}%`);
console.log(`branches: ${branchCovered}/${branchCovered + branchMissed} = ${percent(branchCovered, branchMissed)}%`);
