# Locabike · APK de demonstração

Este projeto empacota a página `/mobile/` do PoC em um APK Android usando
[Capacitor](https://capacitorjs.com/). O aplicativo continua sendo o mesmo código HTML/CSS/JS:
nada é reescrito e nenhuma chamada à API é feita.

A câmera é aberta **dentro do próprio aplicativo**, pelo WebView, e o QR é decodificado no
próprio celular com a biblioteca `jsQR` embarcada. O aplicativo de câmera do sistema não é
acionado. Isso resolve o caso dos Samsung (como o S20+) em que a leitura inteligente de QR do
app de câmera fica indisponível quando a câmera é aberta por outro aplicativo.

## Como o fluxo funciona no celular

1. O painel no computador gera o QR na aba **App**.
2. No APK, toque em **Escanear QR Code** e aponte para o QR. A leitura acontece na tela do próprio
   Locabike, sem tirar foto e sem sair do app.
3. O app aplica o tema, o cliente e o catálogo que vieram no QR.
4. Faça o login de conceito com a senha `123456`, escolha a bicicleta e as horas.
5. O app mostra um código de 36 caracteres; digite-o no painel.
6. O painel cria a locação e mostra `L<id>-XXXXXXXX`; digite essa confirmação no app.
7. A locação confirmada fica salva no aparelho e aparece em **Início** e em **Minhas locações**,
   com bicicleta, datas, status e valor estimado. O menu inferior imita um aplicativo comum:
   **Início**, **Alugar**, **Locações** e **Perfil**.

O APK funciona offline: o QR transporta configuração (tema, cliente, segredo e catálogo) e uma parte do histórico
do cliente. O WebView do Capacitor serve os arquivos locais em
`https://localhost`, portanto `crypto.subtle` e a câmera funcionam em contexto seguro.

## Estrutura

| Caminho | Papel |
|---------|-------|
| `../src/main/resources/static/mobile/` | Fonte única do app (HTML, CSS, JS e `jsQR`) |
| `scripts/preparar.mjs` | Copia o app para `web/` |
| `scripts/apk.mjs` | Executa o Gradle no Windows, Linux ou macOS |
| `web/` | Saída gerada, usada pelo Capacitor (não versionada) |
| `android/` | Projeto Android gerado pelo Capacitor |
| `android/app/build/outputs/apk/debug/app-debug.apk` | APK gerado pelo Gradle |
| `Locabike-debug.apk` | Cópia pronta para transferir ao celular (não versionada) |

## Pré-requisitos

- Node.js 22 ou superior
- JDK 21 para compilar o Android
- Android SDK com `platforms;android-36` e `build-tools;36.0.0` (`ANDROID_HOME`)

No Windows, o projeto de referência usa:

- JDK 21: `%LOCALAPPDATA%\jdk-21`
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`

No Arch Linux, instale o JDK 21 ao lado do Java do sistema:

```bash
sudo pacman -S jdk21-openjdk
```

O script detecta `/usr/lib/jvm/java-21-openjdk` automaticamente. Para outro local de
instalação, defina `LOCABIKE_JAVA_HOME` para a pasta do JDK 21. Essa variável só é
usada para compilar o APK. Configure `ANDROID_HOME` se o SDK não estiver indicado
em `android/local.properties`.

### Instalar o Android SDK no Linux

No Android Studio, abra **SDK Manager** e instale **Android SDK Platform 36** e
**Android SDK Build-Tools 36.0.0**. Com o SDK em `~/Android/Sdk`, o script encontra
o diretório automaticamente.

Para instalar só pela linha de comando, baixe o pacote Linux em **Command line
tools only** na [página oficial do Android](https://developer.android.com/studio#command-tools).
Ajuste o caminho do ZIP se ele estiver em outra pasta. Os comandos abaixo usam
o JDK 21 do Arch Linux durante a instalação do SDK.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
temporario_sdk=$(mktemp -d)
unzip ~/Downloads/commandlinetools-linux-*_latest.zip -d "$temporario_sdk"
mv "$temporario_sdk/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
JAVA_HOME=/usr/lib/jvm/java-21-openjdk "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  --sdk_root="$ANDROID_HOME" "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

Aceite as licenças quando o instalador solicitar. Se o SDK já estiver em outro
diretório, aponte `ANDROID_HOME` para ele ou defina `sdk.dir` em
`android/local.properties`.

## Gerar o APK

```bash
cd mobile-app
npm install
npm run apk
```

O script `npm run apk` copia o app, sincroniza o projeto Android e chama o Gradle correto para o
seu sistema (`gradlew.bat` no Windows, `./gradlew` no Linux/macOS).

No Windows com JDK/SDK fora do `PATH`:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
npm run apk
```

O resultado fica em:

```
mobile-app/android/app/build/outputs/apk/debug/app-debug.apk
```

Já existe uma cópia pronta em `mobile-app/Locabike-debug.apk`. Ela não é atualizada
automaticamente: depois de um novo build, copie o `app-debug.apk` por cima.

Para apenas atualizar os arquivos web depois de mexer em `static/mobile`:

```bash
npm run preparar
npx cap sync android
```

## Instalar no celular

1. Copie `mobile-app/Locabike-debug.apk` (ou o `app-debug.apk` do build) para o celular
   (cabo USB, Google Drive, e-mail ou `adb`).
2. Autorize a instalação de aplicativos de fontes desconhecidas para o app de arquivos/navegador.
3. Abra o APK e instale o **Locabike**.
4. Na primeira leitura de QR, autorize o uso da câmera. Se negar, abra
   **Configurações → Aplicativos → Locabike → Permissões → Câmera** e permita.

Com `adb`:

```bash
adb install -r mobile-app/android/app/build/outputs/apk/debug/app-debug.apk
```

O APK é de depuração, assinado com a chave padrão do Android. Isso é suficiente para a
demonstração, mas não serve para publicar na Play Store.

## Câmera e QR em Samsung

O APK declara a permissão `android.permission.CAMERA` e o WebView do Capacitor pede a permissão
em tempo de execução. A câmera é lida pelo próprio app com `navigator.mediaDevices.getUserMedia`
e o QR é decodificado com `jsQR` (ou `BarcodeDetector`, quando o WebView oferece a API).

Não é necessário abrir o app de câmera do sistema, baixar o módulo de código de barras do ML Kit
nem ter o Google Play Services instalado. Se a câmera não estiver disponível ou a permissão for
negada, selecione uma imagem do QR ou cole seu texto no campo do app.

## Histórico e devolução

A solicitação fica pendente até o usuário digitar a confirmação do painel, vinculada
ao código específico da locação. **Minhas locações** mostra o histórico salvo no
aparelho, separado por cliente, com filtros e detalhes.

Depois de registrar uma devolução no painel, gere e leia um novo QR. O app atualiza
o status, a data de devolução e o valor cobrado com os dados do servidor. O QR
prioriza locações ativas e depois as finalizadas mais recentes, conforme o espaço
disponível. Registros já salvos continuam visíveis no aparelho.

## Limitações

- O APK é um invólucro do PoC: o pareamento continua manual (QR + código digitado).
- O segredo do QR expira em 30 minutos e a locação é criada no painel, não no celular.
- A atualização do histórico depende de ler um novo QR no painel.
- Recompilar o APK exige o toolchain Android; o APK já gerado pode ser instalado sem ele.
