import { cp, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const origem = resolve(raiz, '..', 'src', 'main', 'resources', 'static', 'mobile');
const destino = resolve(raiz, 'web');
const marcador = '<script src="mobile.js"></script>';

await rm(destino, { recursive: true, force: true });
await cp(origem, destino, { recursive: true });

const index = resolve(destino, 'index.html');
let html = await readFile(index, 'utf8');
if (!html.includes(marcador)) {
    throw new Error('index.html do app nao tem o script mobile.js esperado.');
}
html = html.replace(marcador, `<script src="native.js"></script>\n    ${marcador}`);
await writeFile(index, html, 'utf8');

await build({
    entryPoints: [resolve(raiz, 'src', 'native.js')],
    bundle: true,
    format: 'iife',
    target: ['chrome100'],
    outfile: resolve(destino, 'native.js'),
    minify: true,
    legalComments: 'none'
});

console.log(`Web do aplicativo preparada em ${destino}`);
