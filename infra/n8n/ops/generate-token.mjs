// Gera um token aleatório (32 bytes, base64url) para a rotação do token de serviço (feature 004, R-09).
// Roda dentro da imagem do n8n, então o host não precisa de openssl. Imprime só o token; nada é gravado.
import { randomBytes } from 'node:crypto';

process.stdout.write(`${randomBytes(32).toString('base64url')}\n`);
