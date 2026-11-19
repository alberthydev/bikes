/* global BarcodeDetector, crypto, IntersectionObserver */

const CHAVE_CONFIG = 'locabike.mobile.config';
const ALFABETO = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const VALOR_HORA = 5;

const state = {
    config: null,
    usuario: null,
    codigoAtual: null
};

const $ = (seletor) => document.querySelector(seletor);

let cameraStream = null;
let cameraTimer = null;
let detectorQr = null;

/* ============================ utilidades ============================ */

function avisar(mensagem, tipo = 'sucesso') {
    const toast = $('#toast');
    toast.textContent = mensagem;
    toast.className = `toast ${tipo}`;
    clearTimeout(avisar.timer);
    avisar.timer = setTimeout(() => toast.classList.add('oculto'), 4200);
}

function mostrarErro(seletor, mensagem) {
    const alvo = $(seletor);
    alvo.textContent = mensagem;
    alvo.classList.toggle('oculto', !mensagem);
}

function mostrarTela(id) {
    ['telaPareamento', 'telaLogin', 'telaApp'].forEach(tela => {
        document.getElementById(tela).classList.toggle('oculto', tela !== id);
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function corValida(valor) {
    return typeof valor === 'string' && /^#[0-9a-fA-F]{6}$/.test(valor);
}

function aplicarTema(tema) {
    if (!tema) return;
    const raiz = document.documentElement.style;
    if (corValida(tema.primaria)) {
        raiz.setProperty('--primaria', tema.primaria);
        raiz.setProperty('--primaria-escura', escurecer(tema.primaria));
    }
    if (corValida(tema.fundo)) raiz.setProperty('--fundo', tema.fundo);
    if (corValida(tema.texto)) raiz.setProperty('--texto', tema.texto);
    if (Number.isInteger(tema.raio) && tema.raio >= 0 && tema.raio <= 30) {
        raiz.setProperty('--raio', `${tema.raio}px`);
    }
}

function escurecer(hex) {
    const numero = parseInt(hex.slice(1), 16);
    const r = Math.max(0, ((numero >> 16) & 255) - 28);
    const g = Math.max(0, ((numero >> 8) & 255) - 28);
    const b = Math.max(0, (numero & 255) - 28);
    return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`;
}

/* ============================ configuração ============================ */

function validarConfig(dados) {
    if (!dados || dados.v !== 1) throw new Error('Configuração em versão desconhecida.');
    if (typeof dados.seg !== 'string' || dados.seg.length < 16) {
        throw new Error('O QR não tem um segredo de pareamento válido.');
    }
    if (!dados.usuario || !Number.isInteger(dados.usuario.clienteId)) {
        throw new Error('O QR não identifica o cliente da locação.');
    }
    if (!Array.isArray(dados.bikes)) {
        throw new Error('O QR não trouxe a lista de bicicletas.');
    }
    return dados;
}

function usarConfiguracao(texto) {
    try {
        const dados = validarConfig(JSON.parse(texto));
        state.config = dados;
        sessionStorage.setItem(CHAVE_CONFIG, JSON.stringify(dados));
        aplicarTema(dados.tema);
        $('#loginEmail').value = dados.usuario.email || '';
        $('#nomeUsuario').textContent = dados.usuario.nome || 'Cliente';
        $('#emailUsuario').textContent = dados.usuario.email || '';
        mostrarErro('#erroPareamento', '');
        mostrarTela('telaLogin');
        avisar('Configuração aplicada. Faça o login de conceito.');
    } catch (e) {
        mostrarErro('#erroPareamento', `Não foi possível usar o QR: ${e.message}`);
    }
}

/* ============================ câmera ============================ */

async function ligarCamera() {
    mostrarErro('#erroPareamento', '');
    if (!window.isSecureContext) {
        mostrarErro('#erroPareamento', 'A câmera exige HTTPS ou localhost. Cole o texto do QR para continuar.');
        return;
    }
    if (!('BarcodeDetector' in window)) {
        mostrarErro('#erroPareamento', 'Este navegador não tem leitor de QR. Cole o texto do QR exibido no painel.');
        return;
    }
    try {
        cameraStream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: { ideal: 'environment' } },
            audio: false
        });
        const video = $('#videoQr');
        video.srcObject = cameraStream;
        video.classList.remove('oculto');
        await video.play();
        detectorQr = new BarcodeDetector({ formats: ['qr_code'] });
        $('#btnCamera').classList.add('oculto');
        $('#btnPararCamera').classList.remove('oculto');
        cameraTimer = setInterval(procurarQr, 450);
    } catch (e) {
        pararCamera();
        mostrarErro('#erroPareamento', `Não foi possível abrir a câmera: ${e.message}`);
    }
}

async function procurarQr() {
    const video = $('#videoQr');
    if (!detectorQr || video.readyState < 2) return;
    try {
        const codigos = await detectorQr.detect(video);
        if (codigos.length) {
            const texto = codigos[0].rawValue;
            pararCamera();
            usarConfiguracao(texto);
        }
    } catch {
        /* quadro sem QR: continua tentando */
    }
}

function pararCamera() {
    clearInterval(cameraTimer);
    cameraTimer = null;
    detectorQr = null;
    if (cameraStream) {
        cameraStream.getTracks().forEach(trilha => trilha.stop());
        cameraStream = null;
    }
    const video = $('#videoQr');
    if (video) {
        video.srcObject = null;
        video.classList.add('oculto');
    }
    $('#btnCamera').classList.remove('oculto');
    $('#btnPararCamera').classList.add('oculto');
}

/* ============================ login de conceito ============================ */

function entrar(evento) {
    evento.preventDefault();
    const senha = $('#loginSenha').value;
    if (senha !== '123456') {
        avisar('Senha de conceito incorreta. Use 123456.', 'erro');
        return;
    }
    state.usuario = { ...state.config.usuario };
    mostrarTela('telaApp');
    renderizarBicicletas();
    atualizarEstimativa();
    avisar(`Bem-vindo(a), ${state.usuario.nome}.`);
}

function sair() {
    state.usuario = null;
    state.codigoAtual = null;
    $('#areaCodigo').classList.add('oculto');
    $('#areaConfirmacao').classList.add('oculto');
    mostrarTela('telaLogin');
}

/* ============================ locação ============================ */

function renderizarBicicletas() {
    const lista = $('#listaBikes');
    lista.replaceChildren();

    const bikes = state.config.bikes;
    if (!bikes.length) {
        const aviso = document.createElement('p');
        aviso.className = 'mensagem-erro';
        aviso.textContent = 'Nenhuma bicicleta disponível. Gere um novo QR no painel.';
        lista.appendChild(aviso);
        $('#btnGerarCodigo').disabled = true;
        return;
    }
    $('#btnGerarCodigo').disabled = false;

    bikes.forEach((bike, indice) => {
        const rotulo = document.createElement('label');
        rotulo.className = 'bike';

        const radio = document.createElement('input');
        radio.type = 'radio';
        radio.name = 'bike';
        radio.value = String(bike.id);
        radio.checked = indice === 0;

        const texto = document.createElement('div');
        const codigo = document.createElement('strong');
        codigo.textContent = bike.codigo;
        const modelo = document.createElement('span');
        modelo.textContent = bike.modelo || 'Bicicleta disponível';
        texto.append(codigo, modelo);

        rotulo.append(radio, texto);
        lista.appendChild(rotulo);
    });
}

function atualizarEstimativa() {
    const horas = Math.min(24, Math.max(1, Number($('#horas').value) || 1));
    $('#valorEstimado').textContent = `R$ ${(horas * VALOR_HORA).toFixed(2).replace('.', ',')}`;
}

async function gerarCodigo() {
    const selecionada = document.querySelector('input[name="bike"]:checked');
    if (!selecionada) {
        avisar('Escolha uma bicicleta.', 'erro');
        return;
    }
    if (!window.crypto || !crypto.subtle) {
        avisar('Este navegador não tem WebCrypto. Abra o app em localhost ou HTTPS.', 'erro');
        return;
    }

    const botao = $('#btnGerarCodigo');
    botao.disabled = true;
    try {
        const horas = Math.min(24, Math.max(1, Number($('#horas').value) || 1));
        state.codigoAtual = await montarCodigo(
            state.config.usuario.clienteId,
            Number(selecionada.value),
            horas);
        $('#codigoGerado').textContent = state.codigoAtual;
        $('#areaCodigo').classList.remove('oculto');
        $('#areaConfirmacao').classList.remove('oculto');
        $('#areaCodigo').scrollIntoView({ behavior: 'smooth', block: 'center' });
        avisar('Código gerado. Digite-o no painel.');
    } catch (e) {
        avisar(`Falha ao gerar o código: ${e.message}`, 'erro');
    } finally {
        botao.disabled = false;
    }
}

async function montarCodigo(clienteId, bicicletaId, horas) {
    const dados = new Uint8Array(14);
    dados[0] = 1;
    escrever24(dados, 1, clienteId);
    escrever24(dados, 4, bicicletaId);
    escrever32(dados, 7, Math.floor(Date.now() / 60000));
    dados[11] = horas;
    const nonce = new Uint8Array(2);
    crypto.getRandomValues(nonce);
    dados[12] = nonce[0];
    dados[13] = nonce[1];

    const mac = (await hmac(state.config.seg, dados)).slice(0, 8);
    const total = new Uint8Array(dados.length + mac.length);
    total.set(dados, 0);
    total.set(mac, dados.length);
    return formatar(base32(total));
}

async function copiarCodigo() {
    if (!state.codigoAtual) return;
    try {
        await navigator.clipboard.writeText(state.codigoAtual);
        avisar('Código copiado.');
    } catch {
        avisar('Não foi possível copiar. Selecione o código manualmente.', 'erro');
    }
}

async function confirmarLocacao(evento) {
    evento.preventDefault();
    mostrarErro('#erroConfirmacao', '');
    $('#resultadoConfirmacao').classList.add('oculto');

    const valor = $('#confirmacao').value.trim().toUpperCase();
    const partes = valor.match(/^L(\d+)-([0-9A-Z]{4})$/);
    if (!partes) {
        mostrarErro('#erroConfirmacao', 'Formato esperado: L12-AB3D.');
        return;
    }
    if (!window.crypto || !crypto.subtle) {
        mostrarErro('#erroConfirmacao', 'WebCrypto indisponível. Abra o app em localhost ou HTTPS.');
        return;
    }

    const digest = await hmac(state.config.seg, new TextEncoder().encode(`L${partes[1]}`));
    const esperado = base32(digest).slice(0, 4);
    if (esperado !== partes[2]) {
        mostrarErro('#erroConfirmacao', 'Confirmação inválida para este pareamento.');
        return;
    }

    const resultado = $('#resultadoConfirmacao');
    resultado.textContent = `Locação #${partes[1]} confirmada pelo painel. Ciclo concluído!`;
    resultado.classList.remove('oculto');
    avisar('Confirmação validada com o segredo do QR.');
}

/* ============================ código: Base32 e HMAC ============================ */

async function hmac(segredo, dados) {
    const chave = await crypto.subtle.importKey(
        'raw',
        new TextEncoder().encode(segredo),
        { name: 'HMAC', hash: 'SHA-256' },
        false,
        ['sign']);
    return new Uint8Array(await crypto.subtle.sign('HMAC', chave, dados));
}

function escrever24(destino, posicao, valor) {
    destino[posicao] = (valor >>> 16) & 255;
    destino[posicao + 1] = (valor >>> 8) & 255;
    destino[posicao + 2] = valor & 255;
}

function escrever32(destino, posicao, valor) {
    destino[posicao] = (valor >>> 24) & 255;
    destino[posicao + 1] = (valor >>> 16) & 255;
    destino[posicao + 2] = (valor >>> 8) & 255;
    destino[posicao + 3] = valor & 255;
}

function base32(dados) {
    let texto = '';
    let buffer = 0;
    let bits = 0;
    for (const dado of dados) {
        buffer = (buffer << 8) | dado;
        bits += 8;
        while (bits >= 5) {
            texto += ALFABETO[(buffer >>> (bits - 5)) & 31];
            bits -= 5;
        }
    }
    if (bits > 0) {
        texto += ALFABETO[(buffer << (5 - bits)) & 31];
    }
    return texto;
}

function formatar(codigo) {
    return codigo.match(/.{1,6}/g).join('-');
}

/* ============================ eventos ============================ */

function ligarEventos() {
    $('#btnCamera').addEventListener('click', ligarCamera);
    $('#btnPararCamera').addEventListener('click', pararCamera);
    $('#btnUsarConfig').addEventListener('click', () => usarConfiguracao($('#textoConfig').value));
    $('#formLogin').addEventListener('submit', entrar);
    $('#btnCadastro').addEventListener('click', () => $('#dialogCadastro').showModal());
    $('#btnSairApp').addEventListener('click', sair);
    $('#horas').addEventListener('input', atualizarEstimativa);
    $('#btnGerarCodigo').addEventListener('click', gerarCodigo);
    $('#btnCopiarCodigo').addEventListener('click', copiarCodigo);
    $('#btnNovoCodigo').addEventListener('click', () => {
        state.codigoAtual = null;
        $('#areaCodigo').classList.add('oculto');
        $('#areaConfirmacao').classList.add('oculto');
        $('#codigoGerado').textContent = '';
    });
    $('#formConfirmacao').addEventListener('submit', confirmarLocacao);
}

function iniciar() {
    ligarEventos();

    const cripto = window.crypto && crypto.subtle;
    $('#estadoCripto').textContent = cripto
        ? 'WebCrypto ativo'
        : 'WebCrypto indisponível: use localhost ou HTTPS';

    const salvo = sessionStorage.getItem(CHAVE_CONFIG);
    if (salvo) {
        try {
            usarConfiguracao(salvo);
            return;
        } catch {
            sessionStorage.removeItem(CHAVE_CONFIG);
        }
    }
    mostrarTela('telaPareamento');
}

document.addEventListener('DOMContentLoaded', iniciar);
