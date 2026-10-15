#!/usr/bin/env node
// Fatia 7 (T224): renomeação PURA dos identificadores em português que o guardião não conhecia (o inventário final os achou
// com os dicionários pt_BR × en do aspell). Cada regra troca só o nome, nunca texto para pessoas. Conta as trocas de cada
// regra e reprova se alguma não casar (regra morta).
//
//   node rename-leftovers.mjs [--dry-run]
import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const root = resolve(process.cwd());
const dry = process.argv.includes('--dry-run');
const JAVA = 'backend/';
const APP = 'backend/application/src/main/java/br/com/felipeblaft/reports/';
const APPT = 'backend/application/src/test/java/br/com/felipeblaft/reports/';
const CORE = 'backend/core/src/main/java/br/com/felipeblaft/reports/';
const COREX = 'backend/core/src/test/java/br/com/felipeblaft/reports/';

/** { files: lista de caminhos ou prefixo+extensão, re, to } */
const rules = [
  // "até" (limite de tempo): leaseAte / payloadAte
  { all: JAVA, ext: '.java', re: /\bleaseAte\b/g, to: 'leaseUntil' },
  { all: JAVA, ext: '.java', re: /\bpayloadAte\b/g, to: 'payloadUntil' },
  // hora → time (formatador, chave de marcador do catálogo e quem a passa)
  { files: [`${APP}config/MessageFormatting.java`], re: /\bHORA\b/g, to: 'TIME_FORMAT' },
  { all: `${JAVA}application/src/main/java`, ext: '.java', re: /\.hora\(/g, to: '.time(' },
  { files: [`${APP}config/MessageFormatting.java`], re: /public String hora\(/g, to: 'public String time(' },
  { all: `${JAVA}application/src/main/java`, ext: '.java', re: /"hora"/g, to: '"time"' },
  { files: [`${APP}config/MessageFormatting.java`], re: /\bhora\(updated\)/g, to: 'time(updated)' },
  { files: [`${APP}config/MessageFormatting.java`], re: /\bhora\(instant\)/g, to: 'time(instant)' },
  { files: [`${APP}inbound/seed/DemoDataCommand.java`], re: /int hora,/g, to: 'int hour,' },
  { files: [`${APP}inbound/seed/DemoDataCommand.java`], re: /atTime\(hora,/g, to: 'atTime(hour,' },
  { files: ['backend/application/src/main/resources/messages/messages_pt_BR.properties'], re: /\{hora\}/g, to: '{time}' },
  { files: ['frontend/src/pages/ApprovedScreen.tsx'], re: /const hora = /g, to: 'const time = ' },
  { files: ['frontend/src/pages/ApprovedScreen.tsx'], re: /\{ data, hora \}/g, to: '{ data, time }' },
  { files: ['frontend/src/pages/ApprovedScreen.tsx'], re: /\{data\} \{hora\}/g, to: '{data} {time}' },
  { files: ['frontend/src/pages/SemesterScreen.tsx', 'frontend/src/services/history.ts'], re: /\bhora: /g, to: 'time: ' },
  // trinta / quinze / hora (teste da política de retentativa)
  { files: [`${COREX}domain/channel/RetryPolicyTest.java`], re: /\bhora\b/g, to: 'oneHour' },
  { files: [`${COREX}domain/channel/RetryPolicyTest.java`], re: /\btrinta\b/g, to: 'thirtySeconds' },
  { files: [`${COREX}domain/telegram/TelegramConversationTest.java`], re: /\bTRINTA\b/g, to: 'THIRTY' },
  { files: [`${COREX}domain/access/AccessAttemptTest.java`], re: /\bQUINZE\b/g, to: 'FIFTEEN' },
  // plano → plan (massa de demonstração)
  { files: [`${APP}inbound/seed/DemoDataCommand.java`], re: /\bPlano\b(?! por aluno)/g, to: 'Plan' },
  { files: [`${APP}inbound/seed/DemoDataCommand.java`], re: /\bPLANOS\b/g, to: 'PLANS' },
  { files: [`${APP}inbound/seed/DemoDataCommand.java`], re: /\bplano\b(?=[.\s=)])/g, to: 'plan' },
  // alto → high (bits altos do UUID)
  { files: [`${CORE}domain/common/GeneratorId.java`], re: /\balto\b/g, to: 'high' },
  // cadeia → chain (FilterChain)
  { files: [`${APP}inbound/internal/security/ServiceTokenFilter.java`, `${APP}inbound/web/security/SessionCookieFilter.java`], re: /\bcadeia\b/g, to: 'chain' },
  // classe → javaClass (parâmetro de lambda do ArchUnit)
  { files: [`${APPT}architecture/AdapterRulesTest.java`, `${COREX}architecture/ArchitectureRulesTest.java`], re: /\bclasse(?= ->|\.)/g, to: 'javaClass' },
  // par → pair / doPar → forPair / maio → may / signOutPeloButton
  { files: [`${COREX}doubles/ProcessingDoubles.java`], re: /\bdoPar\b/g, to: 'forPair' },
  { files: [`${COREX}usecase/history/QueryStudentHistoryUseCaseTest.java`], re: /\bmaio\b/g, to: 'may' },
  { files: [`${APPT}privacy/LogWithoutSensitiveDataIT.java`], re: /\bpar\b(?= ->|\.key|\.value)/g, to: 'pair' },
  { files: [`${APPT}inbound/telegram/HomeSearchConversationIT.java`], re: /\bsignOutPeloButton\b/g, to: 'signOutByButton' },
  // aba → tab (classe CSS do seletor de abas)
  { files: ['frontend/src/pages/ReviewScreen.tsx', 'frontend/src/pages/SemesterReviewScreen.tsx', 'frontend/src/styles/lesson-registration.css'], re: /\btabs__aba\b/g, to: 'tabs__tab' },
];

const walk = (dir, ext, out = []) => {
  for (const name of readdirSync(dir)) {
    if (['target', 'node_modules', 'db'].includes(name)) continue;
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walk(full, ext, out);
    else if (full.endsWith(ext)) out.push(full.replace(root + '\\', '').replace(/\\/g, '/'));
  }
  return out;
};

const cache = new Map();
const read = (file) => (cache.has(file) ? cache.get(file) : (cache.set(file, readFileSync(join(root, file), 'utf8')), cache.get(file)));
let dead = 0;
rules.forEach((rule, index) => {
  const files = rule.files ?? walk(join(root, rule.all), rule.ext).map((f) => f.replace(/^.*?(backend\/)/, '$1'));
  let hits = 0;
  for (const file of files) {
    const original = read(file);
    const next = original.replace(rule.re, () => {
      hits += 1;
      return rule.to;
    });
    if (next !== original) cache.set(file, next);
  }
  if (hits === 0) {
    dead += 1;
    console.error(`regra sem efeito #${index}: ${rule.re} → ${rule.to}`);
  } else {
    console.log(`#${index} ${rule.re} → ${rule.to}: ${hits}`);
  }
});
if (!dry) for (const [file, text] of cache) writeFileSync(join(root, file), text);
console.log(`${dry ? '[ensaio] ' : ''}${rules.length} regras, ${dead} sem efeito`);
process.exit(dead ? 1 : 0);
