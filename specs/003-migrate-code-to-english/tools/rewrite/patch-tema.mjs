import { readFileSync, writeFileSync } from 'node:fs';
const file = 'frontend/src/app/TemaProvedor.tsx';
let s = readFileSync(file, 'utf8');
s = s.replace("const CHAVE = 'relatorios.tema';", "const CHAVE = 'reports.theme';\n\n/** Valores guardados no navegador (nomes do armazenamento); o tema interno segue 'claro'/'escuro' até a fatia 5. */\nconst GUARDADO: Record<Tema, string> = { claro: 'light', escuro: 'dark' };");
s = s.replace("    if (salvo === 'claro' || salvo === 'escuro') return salvo;", "    if (salvo === GUARDADO.claro) return 'claro';\n    if (salvo === GUARDADO.escuro) return 'escuro';");
s = s.replace("        window.localStorage.setItem(CHAVE, novo);", "        window.localStorage.setItem(CHAVE, GUARDADO[novo]);");
writeFileSync(file, s);
