import { cp, rm } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const origem = resolve(raiz, '..', 'src', 'main', 'resources', 'static', 'mobile');
const destino = resolve(raiz, 'web');

await rm(destino, { recursive: true, force: true });
await cp(origem, destino, { recursive: true });

console.log(`Web do aplicativo preparada em ${destino}`);
