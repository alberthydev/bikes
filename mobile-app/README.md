# Locabike · APK de demonstração

Este projeto empacota a página `/mobile/` do PoC em um APK Android usando
[Capacitor](https://capacitorjs.com/). O aplicativo continua sendo o mesmo código HTML/CSS/JS:
nada é reescrito e nenhuma chamada à API é feita. A única parte nativa é a ponte de câmera, que
abre o aplicativo de câmera do celular, tira uma foto e decodifica o QR com a biblioteca
`jsqr` embarcada no APK.

## Como o fluxo funciona no celular

1. O painel no computador gera o QR na aba **App**.
2. No APK, toque em **Ler QR com a câmera**, fotografe o QR e aguarde a leitura.
3. O app aplica o tema, o cliente e o catálogo que vieram no QR.
4. Faça o login de conceito com a senha `123456`, escolha a bicicleta e as horas.
5. O app mostra um código de 36 caracteres; digite-o no painel.
6. O painel cria a locação e mostra `L<id>-XXXX`; digite essa confirmação no app.

O APK funciona offline: o QR transporta apenas configuração (tema, cliente, segredo e
catálogo), nunca o HTML/CSS/JS do app. O WebView do Capacitor serve os arquivos locais em
`https://localhost`, portanto `crypto.subtle` e a câmera funcionam em contexto seguro.

## Estrutura

| Caminho | Papel |
|---------|-------|
| `../src/main/resources/static/mobile/` | Fonte única do app (HTML, CSS, JS e `jsQR`) |
| `src/native.js` | Ponte de câmera nativa, compilada com esbuild |
| `scripts/preparar.mjs` | Copia o app para `web/` e injeta o script nativo |
| `web/` | Saída gerada, usada pelo Capacitor (não versionada) |
| `android/` | Projeto Android gerado pelo Capacitor |
| `android/app/build/outputs/apk/debug/app-debug.apk` | APK gerado pelo Gradle |
| `Locabike-debug.apk` | Cópia pronta para transferir ao celular (não versionada) |

## Pré-requisitos

- Node.js 22 ou superior
- JDK 21 (`JAVA_HOME`)
- Android SDK com `platforms;android-36` e `build-tools;36.0.0` (`ANDROID_HOME`)

Nesta máquina:

- JDK 21: `%LOCALAPPDATA%\jdk-21`
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`

## Gerar o APK

```powershell
cd mobile-app
npm install

$env:JAVA_HOME = "$env:LOCALAPPDATA\jdk-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

npm run apk
```

O resultado fica em:

```
mobile-app\android\app\build\outputs\apk\debug\app-debug.apk
```

Já existe uma cópia pronta em `mobile-app\Locabike-debug.apk`. Ela não é atualizada
automaticamente: depois de um novo build, copie o `app-debug.apk` por cima.

Para apenas atualizar os arquivos web depois de mexer em `static/mobile`:

```powershell
npm run preparar
npx cap sync android
```

## Instalar no celular

1. Copie `mobile-app\Locabike-debug.apk` (ou o `app-debug.apk` do build) para o celular
   (cabo USB, Google Drive, e-mail ou `adb`).
2. Autorize a instalação de aplicativos de fontes desconhecidas para o app de arquivos/navegador.
3. Abra o APK e instale o **Locabike**.

Com `adb`:

```powershell
adb install -r mobile-app\android\app\build\outputs\apk\debug\app-debug.apk
```

O APK é de depuração, assinado com a chave padrão do Android. Isso é suficiente para a
demonstração, mas não serve para publicar na Play Store.

## Câmera sem Google Play Services

A ponte usa `@capacitor/camera` para abrir a câmera do sistema e `jsqr` para decodificar a foto
no próprio WebView. Não é necessário baixar o módulo de código de barras do ML Kit nem ter o
Google Play Services instalado. Se a câmera não estiver disponível, ainda é possível colar o
texto do QR no campo do app.

## Limitações

- O APK é um invólucro do PoC: o pareamento continua manual (QR + código digitado).
- O segredo do QR expira em 30 minutos e a locação é criada no painel, não no celular.
- Recompilar o APK exige o toolchain Android; o APK já gerado pode ser instalado sem ele.
