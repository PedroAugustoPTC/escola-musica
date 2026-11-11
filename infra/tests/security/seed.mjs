// Semeia a pilha isolada de testes (administrador, 2 professores e 4 alunos) reaproveitando a semeadura do e2e.
import { seed } from '/work/frontend/tests/e2e/support.ts';

const seeding = await seed();
console.log(JSON.stringify({ clara: seeding.clara, bruno: seeding.bruno, students: seeding.students }));
process.exit(0);
