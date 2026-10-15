// Tabela da fatia 6 (infra, scripts e CI): arquivos que mudam de nome, arquivos no escopo da renomeação e as regras de
// troca. É a fonte única de `rewrite/rename-infra.mjs` (aplica) e de `rename-check-infra6.mjs` (verifica): a verificação
// compara cada trecho trocado com o que estas regras permitem, então nada fora desta tabela passa.
//
// Cada regra troca só NOMES (arquivo, serviço, volume, subcomando, variável de shell, segredo, usuário do banco…). Texto
// para pessoas (mensagens, comentários, `name:` de passo) continua em português; quando a frase cita um nome, o nome
// acompanha a troca. Palavras de prosa que também são nomes (`massa`, `arquivo`, `porta`, `interna`…) só trocam em
// posição de código (regex com contexto), nunca em qualquer lugar.

/** Arquivos e pastas movidos (`git mv`). */
export const moves = [
  ['infra/compose.banco.yaml', 'infra/compose.database.yaml'],
  ['infra/carga/exemplo.json', 'infra/seed/example.json'],
  ['infra/carga/demonstracao.json', 'infra/seed/demo.json'],
  ['infra/scripts/implantar.sh', 'infra/scripts/deploy.sh'],
  ['.github/workflows/implantar.yml', '.github/workflows/deploy.yml'],
];

/** Arquivos cujo CONTEÚDO a ferramenta reescreve (caminhos já na posição final). */
export const scope = {
  infra: [
    'infra/.env.example',
    'infra/caddy/Caddyfile',
    'infra/compose.yaml',
    'infra/compose.dev.yaml',
    'infra/compose.test.yaml',
    'infra/compose.database.yaml',
    'infra/scripts/local.sh',
    'infra/scripts/deploy.sh',
    '.github/workflows/ci.yml',
    '.github/workflows/deploy.yml',
    '.gitignore',
    '.dockerignore',
  ],
  // Fora de infra/, mas que carregam os mesmos nomes (imagem, perfil de testes, caminhos de carga, URL do banco).
  outside: [
    'backend/application/Dockerfile',
    'backend/application/src/main/resources/application.yaml',
    'backend/application/src/test/resources/application-test.yaml',
    'backend/application/src/test/java/br/com/felipeblaft/reports/inbound/seed/DemoDataIT.java',
    'backend/application/src/test/java/br/com/felipeblaft/reports/inbound/seed/SeedCommandIT.java',
    'backend/application/src/test/java/br/com/felipeblaft/reports/outbound/persistence/MigrationV6IT.java',
    'backend/application/src/test/java/br/com/felipeblaft/reports/outbound/persistence/MigrationV8ReadableDataIT.java',
    'backend/application/src/test/java/br/com/felipeblaft/reports/support/MigrationTestSupport.java',
    'backend/application/src/test/java/br/com/felipeblaft/reports/support/IntegrationBase.java',
    'backend/application/src/test/java/br/com/felipeblaft/reports/architecture/ArtifactsLanguageGuardIT.java',
    'backend/application/src/test/java/br/com/felipeblaft/reports/outbound/audio/FfmpegAudioNormalizationAdapterIT.java',
    'backend/application/src/main/java/br/com/felipeblaft/reports/inbound/seed/DemoDataCommand.java',
    'frontend/tests/e2e/support.ts',
    'frontend/playwright.config.ts',
  ],
};

/** Documentos de operação que recebem os nomes novos por edição revisada (ver `manualFiles`). */
export const manualFiles = ['infra/README.md', 'infra/scripts/README.md'];

const escape = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const BEFORE = '(?<![A-Za-z0-9_-])';
const AFTER = '(?![A-Za-z0-9_])';

/** Troca literal de um nome, respeitando os limites de palavra (hífen e sublinhado fazem parte do nome). */
const lit = (from, to, files) => ({
  files,
  re: new RegExp(`${BEFORE}${escape(from)}${AFTER}`, 'g'),
  to,
  pairs: [[from, to]],
});
/** Troca literal de um caminho: o hífen antes é permitido (`${1:-infra/carga/…}`). */
const path = (from, to, files) => ({
  files,
  re: new RegExp(`(?<![A-Za-z0-9_])${escape(from)}${AFTER}`, 'g'),
  to,
  pairs: [[from, to]],
});
/** Troca de um trecho (regex) com o par explícito que o verificador deve aceitar. */
const rx = (source, to, pairs, files, flags = 'g') => ({ files, re: new RegExp(source, flags), to, pairs });

const LOCAL = ['infra/scripts/local.sh'];
const DEPLOY = ['infra/scripts/deploy.sh'];
const COMPOSE = ['infra/compose.yaml', 'infra/compose.dev.yaml', 'infra/compose.prod.yaml', 'infra/compose.test.yaml', 'infra/compose.database.yaml'];

export const rules = [
  // --- caminhos e arquivos --------------------------------------------------------------------------------
  path('infra/carga', 'infra/seed'),
  lit('./carga', './seed', COMPOSE),
  lit('compose.banco.yaml', 'compose.database.yaml'),
  lit('implantar.sh', 'deploy.sh'),
  lit('exemplo.json', 'example.json'),
  lit('demonstracao.json', 'demo.json'),
  lit('/dados/documentos', '/data/documents'),
  lit('/carga', '/seed'),

  // --- nomes compostos com `relatorios` (antes do nome solto) ---------------------------------------------
  lit('relatorios-testes', 'reports-tests'),
  lit('relatorios-teste', 'reports-test'),
  lit('relatorios-web', 'reports-web'),
  lit('relatorios-backend', 'reports-backend'),
  lit('relatorios_teste', 'reports_test'),
  lit('relatorios_pgdata', 'reports_pgdata'),
  lit('relatorios_documentos', 'reports_documents'),
  lit('relatorios', 'reports'),
  // diretório de documentos do perfil de testes
  rx('(reports-test/)documentos', '$1documents', [['documentos', 'documents']], ['backend/application/src/test/resources/application-test.yaml']),

  // --- serviços, volumes e rede do compose ----------------------------------------------------------------
  lit('postgres-teste', 'postgres-test'),
  lit('testes-backend', 'tests-backend'),
  lit('testes-frontend', 'tests-frontend'),
  lit('testes-e2e', 'tests-e2e'),
  lit('testes-ia-real', 'tests-real-ai'),
  lit('telegram-bot-api-dados', 'telegram-bot-api-data'),
  lit('caddy-dados', 'caddy-data'),
  rx('^(\\s*-\\s*)documentos:', '$1documents:', [['documentos', 'documents']], COMPOSE, 'gm'),
  rx('^  documentos:$', '  documents:', [['documentos', 'documents']], COMPOSE, 'gm'),
  rx('\\[interna\\]', '[internal]', [['interna', 'internal']], COMPOSE),
  rx('^  interna:$', '  internal:', [['interna', 'internal']], COMPOSE, 'gm'),
  rx('PASSWORD: teste$', 'PASSWORD: test', [['teste', 'test']], ['infra/compose.test.yaml'], 'gm'),
  rx('PASSWORD:teste\\}', 'PASSWORD:test}', [['teste', 'test']], ['backend/application/src/test/resources/application-test.yaml']),
  rx('"DATABASE_PASSWORD", "teste"', '"DATABASE_PASSWORD", "test"', [['teste', 'test']], [
    'backend/application/src/test/java/br/com/felipeblaft/reports/outbound/persistence/MigrationV6IT.java',
    'backend/application/src/test/java/br/com/felipeblaft/reports/support/MigrationTestSupport.java',
  ]),
  rx('(\\n\\s*)System\\.getenv\\(\\)\\.getOrDefault\\("DATABASE_PASSWORD", "teste"\\)', '$1System.getenv().getOrDefault("DATABASE_PASSWORD", "test")', [['teste', 'test']], [
    'backend/application/src/test/java/br/com/felipeblaft/reports/outbound/persistence/MigrationV8ReadableDataIT.java',
  ]),
  lit('FfmpegNormalizacaoAudioAdaptadorIT', 'FfmpegAudioNormalizationAdapterIT', ['infra/compose.test.yaml']),
  rx('segredo-hmac-de-testes-com-mais-de-32-bytes', 'test-hmac-secret-with-more-than-32-bytes', [['segredo-hmac-de-testes-com-mais-de-32-bytes', 'test-hmac-secret-with-more-than-32-bytes']], ['backend/application/src/test/resources/application-test.yaml']),
  rx('"123456:TESTE"', '"123456:TEST"', [['TESTE', 'TEST']], ['backend/application/src/test/resources/application-test.yaml']),

  // comentário do .env.example que citava a chave pelo nome antigo
  rx('ENTREGA=ORCHESTRATOR', 'TELEGRAM_CHANNEL_DELIVERY=ORCHESTRATOR', [['ENTREGA', 'TELEGRAM_CHANNEL_DELIVERY']], ['infra/.env.example']),

  // --- Caddy ----------------------------------------------------------------------------------------------
  lit('@estaticos', '@static', ['infra/caddy/Caddyfile']),
  lit('/fontes/*', '/fonts/*', ['infra/caddy/Caddyfile']),

  // --- workflows ------------------------------------------------------------------------------------------
  lit('jacoco-nucleo', 'jacoco-core', ['.github/workflows/ci.yml']),
  lit('VPS_CHAVE_SSH', 'VPS_SSH_KEY', ['.github/workflows/deploy.yml']),
  lit('VPS_HOST_CONHECIDO', 'VPS_KNOWN_HOST', ['.github/workflows/deploy.yml']),
  lit('VPS_USUARIO', 'VPS_USER', ['.github/workflows/deploy.yml']),
  lit('VPS_DIRETORIO', 'VPS_DIRECTORY', ['.github/workflows/deploy.yml']),
  lit('id_implantacao', 'id_deploy', ['.github/workflows/deploy.yml']),
  rx('^  implantar:$', '  deploy:', [['implantar', 'deploy']], ['.github/workflows/deploy.yml'], 'gm'),
  rx('^  group: implantar$', '  group: deploy', [['implantar', 'deploy']], ['.github/workflows/deploy.yml'], 'gm'),
  rx('environment: producao', 'environment: production', [['producao', 'production']], ['.github/workflows/deploy.yml']),
  rx('"producao"', '"production"', [['producao', 'production']], ['.github/workflows/deploy.yml']),

  // --- ajuda e subcomandos do local.sh (citados também por outros arquivos) -------------------------------
  lit('--massa', '--demo-data'),
  lit('backend/application/target/ia-real/relatorio.md', 'backend/application/target/real-ai/report.md', LOCAL),
  lit('ia-real', 'real-ai'),
  rx('local\\.sh subir', 'local.sh up', [['subir', 'up']]),
  rx('local\\.sh carga \\[arq\\]', 'local.sh seed [file]', [['carga', 'seed'], ['arq', 'file']]),
  rx('local\\.sh carga', 'local.sh seed', [['carga', 'seed']]),
  rx('local\\.sh codigo', 'local.sh code', [['codigo', 'code']]),
  lit('banco-porta', 'database-port'),
  lit('banco-fechar', 'database-close'),
  rx('local\\.sh banco', 'local.sh database', [['banco', 'database']]),
  rx('local\\.sh testes', 'local.sh tests', [['testes', 'tests']]),
  rx('local\\.sh parar', 'local.sh stop', [['parar', 'stop']]),
  rx('local\\.sh limpar', 'local.sh clean', [['limpar', 'clean']]),

  // --- local.sh: variáveis, funções, rótulos do case --------------------------------------------------------
  lit('RAIZ', 'ROOT', [...LOCAL, ...DEPLOY]),
  lit('TESTES', 'TESTS', LOCAL),
  lit('exigir_env', 'require_env', LOCAL),
  lit('zerar_dados', 'reset_data', LOCAL),
  rx('^aguardar\\(\\)', 'wait_until_healthy()', [['aguardar', 'wait_until_healthy']], LOCAL, 'gm'),
  rx('^    aguardar$', '    wait_until_healthy', [['aguardar', 'wait_until_healthy']], LOCAL, 'gm'),
  rx('^servicos\\(\\)', 'services()', [['servicos', 'services']], LOCAL, 'gm'),
  rx('\\$\\(servicos\\)', '$(services)', [['servicos', 'services']], LOCAL),
  rx('^valor\\(\\)', 'value()', [['valor', 'value']], LOCAL, 'gm'),
  rx('\\$\\(valor ', '$(value ', [['valor', 'value']], LOCAL),
  rx('\\bchave\\b(?= in\\b|")', 'key', [['chave', 'key']], LOCAL),
  rx('\\bfaltando\\b(?==)', 'missing', [['faltando', 'missing']], LOCAL),
  rx('\\$faltando\\b', '$missing', [['faltando', 'missing']], LOCAL),
  rx('^  tentativas=0$', '  attempts=0', [['tentativas', 'attempts']], LOCAL, 'gm'),
  rx('\\btentativas\\b(?==)', 'attempts', [['tentativas', 'attempts']], [...LOCAL, ...DEPLOY]),
  rx('\\$tentativas\\b', '$attempts', [['tentativas', 'attempts']], [...LOCAL, ...DEPLOY]),
  rx('\\(tentativas \\+ 1\\)', '(attempts + 1)', [['tentativas', 'attempts']], [...LOCAL, ...DEPLOY]),
  rx('\\bestados=', 'states=', [['estados', 'states']], LOCAL),
  rx('"\\$estados"', '"$states"', [['estados', 'states']], LOCAL),
  rx('\\bcomando="', 'command="', [['comando', 'command']], LOCAL),
  rx('"\\$comando"', '"$command"', [['comando', 'command']], LOCAL),
  rx('\\$comando\\b', '$command', [['comando', 'command']], LOCAL),
  rx(':-ajuda\\}', ':-help}', [['ajuda', 'help']], LOCAL),
  rx('^  ajuda\\|-h\\|--help\\)', '  help|-h|--help)', [['ajuda', 'help']], LOCAL, 'gm'),
  rx('^  subir\\)', '  up)', [['subir', 'up']], LOCAL, 'gm'),
  rx('^  carga\\)', '  seed)', [['carga', 'seed']], LOCAL, 'gm'),
  rx('^  codigo\\)', '  code)', [['codigo', 'code']], LOCAL, 'gm'),
  rx('^  banco\\)', '  database)', [['banco', 'database']], LOCAL, 'gm'),
  rx('^  testes\\)', '  tests)', [['testes', 'tests']], LOCAL, 'gm'),
  rx('^  parar\\)', '  stop)', [['parar', 'stop']], LOCAL, 'gm'),
  rx('^  limpar\\)', '  clean)', [['limpar', 'clean']], LOCAL, 'gm'),
  // variáveis de shell cujo nome também é palavra de prosa: só em posição de código
  rx('^    massa=""$', '    demo_data=""', [['massa', 'demo_data']], LOCAL, 'gm'),
  rx('\\) massa="\\$\\{1#', ') demo_data="${1#', [['massa', 'demo_data']], LOCAL),
  rx('--demo-data\\) massa="', '--demo-data) demo_data="', [['massa', 'demo_data']], LOCAL),
  rx('\\$massa\\b', '$demo_data', [['massa', 'demo_data']], LOCAL),
  rx('\\bsaida_massa\\b', 'demo_data_output', [['saida_massa', 'demo_data_output']], LOCAL),
  rx('^    arquivo="', '    file="', [['arquivo', 'file']], LOCAL, 'gm'),
  rx('case "\\$arquivo"', 'case "$file"', [['arquivo', 'file']], LOCAL),
  rx('\\$\\{arquivo#', '${file#', [['arquivo', 'file']], LOCAL),
  rx('^    porta="', '    port="', [['porta', 'port']], LOCAL, 'gm'),
  rx('\\$\\{porta:-', '${port:-', [['porta', 'port']], LOCAL),
  rx('^    qual="', '    suite="', [['qual', 'suite']], LOCAL, 'gm'),
  rx('case "\\$qual"', 'case "$suite"', [['qual', 'suite']], LOCAL),
  rx('\\$\\{1:-todos\\}', '${1:-all}', [['todos', 'all']], LOCAL),
  rx('^      todos\\)', '      all)', [['todos', 'all']], LOCAL, 'gm'),
  rx('real-ai \\| todos', 'real-ai | all', [['todos', 'all']], LOCAL),
  rx('\\(padrão: todos\\)', '(padrão: all)', [['todos', 'all']], LOCAL),
  rx('\\[qual\\]', '[suite]', [['qual', 'suite']], LOCAL),
  rx('^    read -r confirmacao$', '    read -r confirmation', [['confirmacao', 'confirmation']], LOCAL, 'gm'),
  rx('"\\$confirmacao"', '"$confirmation"', [['confirmacao', 'confirmation']], LOCAL),

];

/**
 * Edições revisadas à mão, fora da tabela de troca de nomes (cada uma explicada em `evidence/slice-6.md`):
 * - a orientação para quem tem `infra/.env` anterior à feature (T181), que é mensagem de script e não renomeação;
 * - a correção do perfil do executor dos comandos operacionais, que a fatia 3 deixou com o nome antigo (`carga & !teste`)
 *   enquanto o `docker-entrypoint.sh` já ativava `seed`, e a mensagem de uso desse executor.
 */
export const reviewed = [
  {
    file: 'infra/scripts/local.sh',
    from: '',
    to: '    echo "Se o seu infra/.env é anterior à feature 003, regere-o a partir de infra/.env.example." >&2',
  },
  {
    file: 'backend/application/src/main/java/br/com/felipeblaft/reports/inbound/seed/SeedCommandsExecutor.java',
    from: '{@code carga}',
    to: '{@code seed}',
  },
  {
    file: 'backend/application/src/main/java/br/com/felipeblaft/reports/inbound/seed/SeedCommandsExecutor.java',
    from: '@Profile("carga & !teste")',
    to: '@Profile("seed & !test")',
  },
  {
    file: 'backend/application/src/main/java/br/com/felipeblaft/reports/inbound/seed/SeedCommandsExecutor.java',
    from: '| massa --arquivo <cadastros.json>',
    to: '| demo-data --file <cadastros.json>',
  },
];
