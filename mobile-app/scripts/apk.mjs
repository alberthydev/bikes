import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const android = resolve(raiz, 'android');
const windows = process.platform === 'win32';
const linux = process.platform === 'linux';
const { LOCABIKE_JAVA_HOME, JAVA_HOME, LOCALAPPDATA } = process.env;

function versaoJdk(diretorio) {
    const javac = windows ? 'javac.exe' : 'javac';
    const executavel = diretorio ? join(resolve(diretorio), 'bin', javac) : javac;
    const resultado = spawnSync(executavel, ['-version'], { encoding: 'utf8' });
    return resultado.status === 0
        ? (resultado.stdout + resultado.stderr).match(/javac (\d+)/)?.[1]
        : undefined;
}

const instalacoes = LOCABIKE_JAVA_HOME ? [LOCABIKE_JAVA_HOME] : [
    JAVA_HOME,
    linux && '/usr/lib/jvm/java-21-openjdk',
    windows && LOCALAPPDATA && join(LOCALAPPDATA, 'jdk-21')
].filter(Boolean);
const javaHome = instalacoes.find(diretorio => versaoJdk(diretorio) === '21');
const javaNoPath = spawnSync('java', ['-version'], { encoding: 'utf8' });
const jdk21NoPath = javaNoPath.status === 0
    && /version "21(?:[.+"-])/.test(javaNoPath.stdout + javaNoPath.stderr)
    && versaoJdk() === '21';
if (!javaHome && (LOCABIKE_JAVA_HOME || !jdk21NoPath)) {
    console.error('O APK precisa do JDK 21. Instale-o e aponte LOCABIKE_JAVA_HOME para a pasta do JDK.');
    if (linux) {
        console.error('No Arch Linux: sudo pacman -S jdk21-openjdk');
    }
    process.exit(1);
}

const env = { ...process.env };
if (javaHome) {
    env.JAVA_HOME = resolve(javaHome);
} else {
    delete env.JAVA_HOME;
}


if (!env.ANDROID_HOME && !env.ANDROID_SDK_ROOT && !existsSync(join(android, 'local.properties'))) {
    const locaisSdk = [
        linux && join(homedir(), 'Android', 'Sdk'),
        linux && '/opt/android-sdk',
        process.platform === 'darwin' && join(homedir(), 'Library', 'Android', 'sdk'),
        windows && LOCALAPPDATA && join(LOCALAPPDATA, 'Android', 'Sdk')
    ].filter(Boolean);
    const sdk = locaisSdk.find(diretorio => existsSync(join(diretorio, 'platforms')));
    if (!sdk) {
        console.error('Android SDK não encontrado. Instale a plataforma Android 36 e o Build Tools 36.0.0.');
        console.error('Veja mobile-app/README.md para instalar o SDK ou configurar ANDROID_HOME.');
        process.exit(1);
    }
    env.ANDROID_HOME = sdk;
}

// No Windows o wrapper e um .bat; nos demais sistemas chamamos o script via `sh`
// para nao depender da permissao de execucao do arquivo no checkout do Git.
const comando = windows ? 'gradlew.bat' : 'sh';
const argumentos = windows ? ['assembleDebug'] : ['gradlew', 'assembleDebug'];

const resultado = spawnSync(comando, argumentos, {
    cwd: android,
    env,
    stdio: 'inherit',
    shell: windows
});

if (resultado.error) {
    console.error(`Nao foi possivel executar o Gradle: ${resultado.error.message}`);
}

process.exit(resultado.status ?? 1);
