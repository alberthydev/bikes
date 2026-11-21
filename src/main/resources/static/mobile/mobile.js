/* global BarcodeDetector, crypto */

const CHAVE_CONFIG = 'locabike.mobile.config';
const CHAVE_SESSAO = 'locabike.mobile.sessao';
const CHAVE_LOCACOES = 'locabike.mobile.locacoes';
const CHAVE_SOLICITACAO = 'locabike.mobile.solicitacao';
const CHAVE_CONFIG_SESSAO_ANTIGA = 'locabike.mobile.config';
const ALFABETO = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const VALOR_HORA = 5;

const state = {
    config: null,
    usuario: null,
    codigoAtual: null,
    solicitacaoAtual: null,
    locacaoAtiva: null,
    aba: 'inicio',
    filtro: 'todas',
    timer: null,
    procurandoQr: false
};

const $ = (seletor, raiz = document) => raiz.querySelector(seletor);
const $$ = (seletor, raiz = document) => Array.from(raiz.querySelectorAll(seletor));

let cameraStream = null;
let cameraSessao = 0;
let cameraTimer = null;
let detectorQr = null;
let canvasQr = null;

const NOMES_ABA = {
    inicio: 'Início',
    alugar: 'Alugar bicicleta',
    locacoes: 'Minhas locações',
    perfil: 'Perfil'
};

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
    if (!alvo) return;
    alvo.textContent = mensagem;
    alvo.classList.toggle('oculto', !mensagem);
}

function esc(valor) {
    return String(valor ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;');
}

function lerJson(chave, armazenamento = localStorage) {
    try {
        const texto = armazenamento.getItem(chave);
        return texto ? JSON.parse(texto) : null;
    } catch {
        return null;
    }
}

function guardarJson(chave, valor, armazenamento = localStorage) {
    try {
        armazenamento.setItem(chave, JSON.stringify(valor));
    } catch {
        /* armazenamento indisponível */
    }
}

function primeiroNome(nome) {
    return String(nome || 'cliente').trim().split(/\s+/)[0] || 'cliente';
}

function iniciais(nome) {
    const partes = String(nome || 'Locabike').trim().split(/\s+/).filter(Boolean);
    const primeira = partes[0]?.[0] || 'L';
    const segunda = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return `${primeira}${segunda}`.toUpperCase();
}

function formatarDataHora(iso) {
    if (!iso) return '—';
    const data = new Date(iso);
    if (Number.isNaN(data.getTime())) return esc(iso);
    return data.toLocaleString('pt-BR', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

function formatarDataCurta(iso) {
    if (!iso) return '—';
    const data = new Date(iso);
    if (Number.isNaN(data.getTime())) return esc(iso);
    return data.toLocaleString('pt-BR', {
        day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
    });
}

function formatarMoeda(valor) {
    if (valor === null || valor === undefined) return '—';
    return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

function formatarDuracao(minutos) {
    if (minutos === null || minutos === undefined || !Number.isFinite(Number(minutos))) return '—';
    const total = Math.max(0, Math.round(Number(minutos)));
    const horas = Math.floor(total / 60);
    const resto = total % 60;
    if (!horas) return `${resto} min`;
    return resto ? `${horas}h ${resto}min` : `${horas}h`;
}

function formatarDuracaoCompacta(minutos) {
    const total = Math.max(0, Math.round(Number(minutos) || 0));
    const horas = Math.floor(total / 60);
    const resto = total % 60;
    if (!horas) return `${resto}min`;
    return resto ? `${horas}h${String(resto).padStart(2, '0')}` : `${horas}h`;
}

function formatarRestante(milissegundos) {
    const minutos = Math.ceil(Math.max(0, milissegundos) / 60000);
    const horas = Math.floor(minutos / 60);
    const resto = minutos % 60;
    if (!horas) return `${resto}min`;
    return resto ? `${horas}h ${resto}min` : `${horas}h`;
}

function corValida(valor) {
    return typeof valor === 'string' && /^#[0-9a-fA-F]{6}$/.test(valor);
}

function escurecer(hex) {
    const numero = parseInt(hex.slice(1), 16);
    const r = Math.max(0, ((numero >> 16) & 255) - 28);
    const g = Math.max(0, ((numero >> 8) & 255) - 28);
    const b = Math.max(0, (numero & 255) - 28);
    return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`;
}

function clarear(hex) {
    const numero = parseInt(hex.slice(1), 16);
    const misturar = (canal) => Math.round(canal * 0.14 + 255 * 0.86);
    const r = misturar((numero >> 16) & 255);
    const g = misturar((numero >> 8) & 255);
    const b = misturar(numero & 255);
    return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`;
}

function aplicarTema(tema) {
    if (!tema) return;
    const raiz = document.documentElement.style;
    if (corValida(tema.primaria)) {
        raiz.setProperty('--primaria', tema.primaria);
        raiz.setProperty('--primaria-escura', escurecer(tema.primaria));
        raiz.setProperty('--primaria-clara', clarear(tema.primaria));
        const meta = document.querySelector('meta[name="theme-color"]');
        if (meta) meta.setAttribute('content', tema.primaria);
    }
    if (corValida(tema.fundo)) raiz.setProperty('--fundo', tema.fundo);
    if (corValida(tema.texto)) raiz.setProperty('--texto', tema.texto);
    if (Number.isInteger(tema.raio) && tema.raio >= 0 && tema.raio <= 30) {
        raiz.setProperty('--raio', `${tema.raio}px`);
    }
}

/* ============================ telas ============================ */

function mostrarTela(id) {
    if (id !== 'telaPareamento') pararCamera();
    ['telaPareamento', 'telaLogin', 'telaApp'].forEach(tela => {
        document.getElementById(tela).classList.toggle('oculto', tela !== id);
    });
    const logado = id === 'telaApp';
    document.body.classList.toggle('tem-menu', logado);
    $('#menuInferior').classList.toggle('oculto', !logado);
    $('#btnTopoPerfil').classList.toggle('oculto', !logado);
    $('#topoChip').classList.toggle('oculto', logado);
    const podeVoltar = Boolean(state.usuario || state.config);
    $('#btnVoltarApp').classList.toggle('oculto', !(id === 'telaPareamento' && podeVoltar));
    $('#btnVoltarApp').textContent = state.usuario ? 'Voltar ao aplicativo' : 'Voltar ao login';
    if (logado) {
        atualizarTopo();
    } else {
        $('#topoSubtitulo').textContent = 'Aluguel de bicicletas';
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function atualizarTopo() {
    $('#topoSubtitulo').textContent = state.usuario
        ? (NOMES_ABA[state.aba] || 'Locabike')
        : 'Aluguel de bicicletas';
    $('#btnTopoPerfil').textContent = iniciais(state.usuario?.nome || 'Locabike');
}

function irPara(aba) {
    if (!state.usuario) return;
    if (!NOMES_ABA[aba]) return;
    state.aba = aba;

    $$('#menuInferior .menu-item').forEach(botao => {
        const ativo = botao.dataset.aba === aba;
        botao.classList.toggle('ativo', ativo);
        botao.setAttribute('aria-current', ativo ? 'page' : 'false');
    });
    $$('.aba-conteudo').forEach(tela => {
        tela.classList.toggle('oculto', tela.id !== `aba${aba.charAt(0).toUpperCase()}${aba.slice(1)}`);
    });

    if (aba === 'inicio') renderizarInicio();
    if (aba === 'alugar') renderizarAlugar();
    if (aba === 'locacoes') renderizarLocacoes();
    if (aba === 'perfil') renderizarPerfil();

    atualizarTopo();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* ============================ configuração ============================ */

function validarConfig(dados) {
    if (!dados || Number(dados.v) !== 1) throw new Error('Configuração em versão desconhecida.');
    if (typeof dados.seg !== 'string' || dados.seg.length < 16) {
        throw new Error('O QR não tem um segredo de pareamento válido.');
    }
    if (!dados.usuario || !Number.isInteger(Number(dados.usuario.clienteId))) {
        throw new Error('O QR não identifica o cliente da locação.');
    }
    if (!Array.isArray(dados.bikes)) {
        throw new Error('O QR não trouxe a lista de bicicletas.');
    }
    dados.usuario.clienteId = Number(dados.usuario.clienteId);
    return dados;
}

function configExpirada() {
    if (!state.config?.exp) return false;
    const expira = new Date(state.config.exp).getTime();
    return Number.isFinite(expira) && expira <= Date.now();
}

function usarConfiguracao(texto) {
    try {
        const dados = validarConfig(JSON.parse(texto));
        state.config = dados;
        state.usuario = null;
        localStorage.removeItem(CHAVE_SESSAO);
        try {
            sessionStorage.removeItem(CHAVE_CONFIG_SESSAO_ANTIGA);
        } catch { /* ignore */ }
        guardarJson(CHAVE_CONFIG, dados);
        aplicarTema(dados.tema);
        $('#loginEmail').value = dados.usuario.email || '';
        $('#avisoPareamentoLogin').classList.toggle('oculto', !configExpirada());
        mostrarErro('#erroPareamento', '');
        restaurarSolicitacao();
        atualizarHistorico(dados);
        mostrarTela('telaLogin');
        avisar('Pareamento atualizado. Faça o login de demonstração.');
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
    if (!navigator.mediaDevices?.getUserMedia) {
        mostrarErro('#erroPareamento', 'Este navegador não expõe a câmera para a página. Cole o texto do QR exibido no painel.');
        return;
    }
    if (!('BarcodeDetector' in window) && !window.jsQR) {
        mostrarErro('#erroPareamento', 'Este navegador não tem leitor de QR. Cole o texto do QR exibido no painel.');
        return;
    }

    pararCamera();
    const sessao = cameraSessao;
    $('#btnCamera').disabled = true;
    try {
        const stream = await navigator.mediaDevices.getUserMedia({
            video: {
                facingMode: { ideal: 'environment' },
                width: { ideal: 1280 },
                height: { ideal: 720 }
            },
            audio: false
        });

        if (sessao !== cameraSessao) {
            stream.getTracks().forEach(trilha => trilha.stop());
            return;
        }
        cameraStream = stream;
        const video = $('#videoQr');
        video.srcObject = stream;
        $('#cameraArea').classList.remove('oculto');
        await video.play();
        if (sessao !== cameraSessao) return;

        try {
            detectorQr = 'BarcodeDetector' in window
                ? new BarcodeDetector({ formats: ['qr_code'] })
                : null;
        } catch {
            detectorQr = null;
        }

        $('#btnCamera').classList.add('oculto');
        $('#btnPararCamera').classList.remove('oculto');
        $('#btnLanterna').classList.toggle('oculto', !stream.getVideoTracks()[0]?.getCapabilities?.().torch);
        $('#statusCamera').textContent = 'Aponte a câmera para o QR exibido no painel.';
        $('#statusCamera').classList.remove('oculto');
        cameraTimer = setInterval(procurarQr, 320);
    } catch (e) {
        if (sessao !== cameraSessao) return;
        pararCamera();
        mostrarErro('#erroPareamento', `Não foi possível abrir a câmera: ${e.message}. Você também pode selecionar uma imagem do QR.`);
    }
}

async function procurarQr() {
    if (state.procurandoQr) return;
    const video = $('#videoQr');
    if (!video || video.readyState < 2 || !video.videoWidth) return;

    const sessao = cameraSessao;
    state.procurandoQr = true;
    try {
        let texto = null;
        if (detectorQr) {
            try {
                const codigos = await detectorQr.detect(video);
                texto = codigos[0]?.rawValue;
            } catch {
                detectorQr = null;
            }
        }
        if (sessao !== cameraSessao) return;
        if (!texto && window.jsQR) {
            texto = lerQrDaImagem(video, video.videoWidth, video.videoHeight);
        }
        if (texto) usarConfiguracao(texto);
    } catch {
        /* quadro sem QR: continua tentando */
    } finally {
        if (sessao === cameraSessao) state.procurandoQr = false;
    }
}

function lerQrDaImagem(imagem, largura, altura) {
    const escala = Math.min(1, 1600 / Math.max(largura, altura));
    if (!canvasQr) canvasQr = document.createElement('canvas');
    canvasQr.width = Math.round(largura * escala);
    canvasQr.height = Math.round(altura * escala);
    const contexto = canvasQr.getContext('2d', { willReadFrequently: true });
    contexto.drawImage(imagem, 0, 0, canvasQr.width, canvasQr.height);
    const pixels = contexto.getImageData(0, 0, canvasQr.width, canvasQr.height);
    const codigo = window.jsQR(pixels.data, canvasQr.width, canvasQr.height, {
        inversionAttempts: 'attemptBoth'
    });
    return codigo ? codigo.data : null;
}

async function lerArquivoQr(evento) {
    const arquivo = evento.target.files[0];
    evento.target.value = '';
    if (!arquivo) return;
    pararCamera();
    const sessao = cameraSessao;
    const url = URL.createObjectURL(arquivo);
    try {
        const imagem = new Image();
        imagem.src = url;
        await imagem.decode();
        if (sessao !== cameraSessao) return;
        const texto = lerQrDaImagem(imagem, imagem.naturalWidth, imagem.naturalHeight);
        if (texto) usarConfiguracao(texto);
        else mostrarErro('#erroPareamento', 'Nenhum QR encontrado na imagem selecionada.');
    } catch {
        if (sessao === cameraSessao) mostrarErro('#erroPareamento', 'Não foi possível ler a imagem selecionada.');
    } finally {
        URL.revokeObjectURL(url);
    }
}

async function alternarLanterna() {
    const trilha = cameraStream?.getVideoTracks()[0];
    if (!trilha) return;
    const capacidades = trilha.getCapabilities ? trilha.getCapabilities() : {};
    if (!capacidades.torch) {
        avisar('A lanterna não está disponível nesta câmera.', 'erro');
        return;
    }
    try {
        const ligada = trilha.getSettings().torch === true;
        await trilha.applyConstraints({ advanced: [{ torch: !ligada }] });
        $('#btnLanterna').textContent = ligada ? 'Lanterna' : 'Desligar lanterna';
    } catch {
        avisar('Não foi possível controlar a lanterna.', 'erro');
    }
}

function pararCamera() {
    cameraSessao += 1;
    $('#btnCamera').disabled = false;
    clearInterval(cameraTimer);
    cameraTimer = null;
    state.procurandoQr = false;
    detectorQr = null;
    if (cameraStream) {
        cameraStream.getTracks().forEach(trilha => trilha.stop());
        cameraStream = null;
    }
    const video = $('#videoQr');
    if (video) {
        video.srcObject = null;
    }
    $('#cameraArea').classList.add('oculto');
    $('#statusCamera').classList.add('oculto');
    $('#btnCamera').classList.remove('oculto');
    $('#btnPararCamera').classList.add('oculto');
    $('#btnLanterna').classList.add('oculto');
    $('#btnLanterna').textContent = 'Lanterna';
}

/* ============================ login ============================ */

function entrar(evento) {
    evento.preventDefault();
    if (!state.config) {
        mostrarTela('telaPareamento');
        avisar('Leia o QR de pareamento antes de entrar.', 'erro');
        return;
    }
    const senha = $('#loginSenha').value;
    if (senha !== '123456') {
        avisar('Senha de conceito incorreta. Use 123456.', 'erro');
        return;
    }

    state.usuario = { ...state.config.usuario };
    guardarJson(CHAVE_SESSAO, {
        clienteId: state.usuario.clienteId,
        nome: state.usuario.nome,
        email: state.usuario.email
    });
    mostrarTela('telaApp');
    irPara('inicio');
    iniciarRelogio();
    if (configExpirada()) {
        avisar('Bem-vindo(a). O pareamento expirou: leia um novo QR para alugar.', 'erro');
    } else {
        avisar(`Bem-vindo(a), ${primeiroNome(state.usuario.nome)}.`);
    }
}

function sair() {
    state.usuario = null;
    localStorage.removeItem(CHAVE_SESSAO);
    clearInterval(state.timer);
    state.timer = null;
    resetAlugar();
    mostrarTela('telaLogin');
    $('#avisoPareamentoLogin').classList.toggle('oculto', !configExpirada());
    avisar('Sessão encerrada.');
}

function iniciarRelogio() {
    clearInterval(state.timer);
    state.timer = setInterval(() => {
        if (!state.usuario) return;
        if (state.aba === 'inicio') renderizarInicio();
        if (state.aba === 'locacoes') renderizarLocacoes();
    }, 30000);
}

/* ============================ histórico local ============================ */

function lerTodasLocacoes() {
    const lista = lerJson(CHAVE_LOCACOES);
    return Array.isArray(lista) ? lista : [];
}

function salvarTodasLocacoes(lista) {
    guardarJson(CHAVE_LOCACOES, lista);
}

function clienteIdAtual() {
    return Number(state.usuario?.clienteId ?? state.config?.usuario?.clienteId);
}

function locacoesDoUsuario() {
    const clienteId = clienteIdAtual();
    if (!Number.isFinite(clienteId)) return [];
    return lerTodasLocacoes()
        .filter(locacao => Number(locacao.clienteId) === clienteId)
        .sort((a, b) => new Date(b.retirada).getTime() - new Date(a.retirada).getTime());
}

function salvarLocacao(registro) {
    const todas = lerTodasLocacoes();
    const indice = todas.findIndex(locacao =>
        Number(locacao.id) === Number(registro.id)
        && Number(locacao.clienteId) === Number(registro.clienteId));
    if (indice >= 0) {
        todas[indice] = { ...todas[indice], ...registro };
    } else {
        todas.push(registro);
    }
    salvarTodasLocacoes(todas);
}

function valorPorPeriodo(retirada, fim) {
    const inicio = new Date(retirada).getTime();
    const termino = new Date(fim).getTime();
    if (!Number.isFinite(inicio) || !Number.isFinite(termino) || termino <= inicio) {
        return VALOR_HORA;
    }
    const horas = Math.max(1, Math.ceil((termino - inicio) / 3600000));
    return horas * VALOR_HORA;
}

function valorAtual(locacao) {
    if (locacao.status === 'FINALIZADA' && locacao.valor !== null && locacao.valor !== undefined) {
        return Number(locacao.valor);
    }
    return valorPorPeriodo(locacao.retirada, new Date());
}

function duracaoAtual(locacao) {
    const inicio = new Date(locacao.retirada).getTime();
    const fim = locacao.devolucaoEfetiva ? new Date(locacao.devolucaoEfetiva).getTime() : Date.now();
    if (!Number.isFinite(inicio) || !Number.isFinite(fim)) return 0;
    return Math.max(0, Math.floor((fim - inicio) / 60000));
}

function statusDe(locacao) {
    if (locacao.status === 'FINALIZADA') {
        return { chave: 'finalizada', rotulo: 'Finalizada' };
    }
    const previsao = new Date(locacao.previsaoDevolucao).getTime();
    if (Number.isFinite(previsao) && previsao < Date.now()) {
        return { chave: 'atrasada', rotulo: 'Atrasada' };
    }
    return { chave: 'ativa', rotulo: 'Em andamento' };
}

function atualizarHistorico(dados) {
    if (!Array.isArray(dados.locacoes)) return;
    const todas = lerTodasLocacoes();
    const clienteId = dados.usuario.clienteId;

    const pendente = state.solicitacaoAtual;
    const previsaoPendente = pendente && (pendente.retirada.getTime() + pendente.horas * 3600000);
    for (const locacao of dados.locacoes) {
        if (!Number.isSafeInteger(locacao?.id) || locacao.id < 1) continue;
        if (pendente && locacao.bicicletaId === Number(pendente.bike.id)
            && Date.parse(locacao.previsaoDevolucao) === previsaoPendente) continue;
        const indice = todas.findIndex(item =>
            Number(item.clienteId) === clienteId && Number(item.id) === locacao.id);
        const anterior = todas[indice];
        if (anterior?.dadosDoPainel && anterior.status === 'FINALIZADA') continue;
        const registro = {
            ...anterior,
            ...locacao,
            clienteId,
            dadosDoPainel: true
        };
        if (indice === -1) todas.push(registro);
        else todas[indice] = registro;
    }
    salvarTodasLocacoes(todas);
}

/* ============================ início ============================ */

function renderizarInicio() {
    const usuario = state.usuario || {};
    $('#saudacaoNome').textContent = `Olá, ${primeiroNome(usuario.nome)}!`;
    $('#saudacaoEmail').textContent = usuario.email || '';

    const locacoes = locacoesDoUsuario();
    const ativas = locacoes.filter(locacao => locacao.status === 'ATIVA');
    const ativa = ativas[0] || null;
    state.locacaoAtiva = ativa;

    const cartaoAtiva = $('#cartaoLocacaoAtiva');
    const cartaoVazio = $('#cartaoSemLocacao');
    if (ativa) {
        const status = statusDe(ativa);
        const previsao = new Date(ativa.previsaoDevolucao).getTime();
        const restante = Number.isFinite(previsao) ? previsao - Date.now() : 0;

        cartaoAtiva.classList.remove('oculto');
        cartaoVazio.classList.add('oculto');
        $('#ativaStatus').className = `selo ${status.chave}`;
        $('#ativaStatus').textContent = status.rotulo;
        $('#ativaTempo').textContent = restante > 0
            ? `Restam ${formatarRestante(restante)}`
            : 'Previsão vencida';
        $('#ativaBike').textContent = ativa.codigoBicicleta || 'Bicicleta';
        $('#ativaModelo').textContent = ativa.modeloBicicleta || 'Locação ativa';
        $('#ativaRetirada').textContent = formatarDataCurta(ativa.retirada);
        $('#ativaPrevisao').textContent = formatarDataCurta(ativa.previsaoDevolucao);
        $('#ativaValor').textContent = formatarMoeda(valorAtual(ativa));
    } else {
        cartaoAtiva.classList.add('oculto');
        cartaoVazio.classList.remove('oculto');
    }

    const minutos = locacoes.reduce((soma, locacao) => soma + duracaoAtual(locacao), 0);
    const valor = locacoes.reduce((soma, locacao) => soma + valorAtual(locacao), 0);
    $('#statTotal').textContent = String(locacoes.length);
    $('#statHoras').textContent = formatarDuracaoCompacta(minutos);
    $('#statValor').textContent = formatarMoeda(valor);
}

/* ============================ alugar ============================ */

function renderizarAlugar() {
    renderizarBicicletas();
    atualizarEstimativa();
    $('#avisoAlugarExpirado').classList.toggle('oculto', !configExpirada());

    if (state.codigoAtual && state.solicitacaoAtual) {
        $('#codigoGerado').textContent = state.codigoAtual;
        $('#codigoResumo').textContent =
            `${state.solicitacaoAtual.bike.codigo} · ${state.solicitacaoAtual.horas}h`;
        $('#areaCodigo').classList.remove('oculto');
        $('#areaConfirmacao').classList.remove('oculto');
    } else {
        $('#areaCodigo').classList.add('oculto');
        $('#areaConfirmacao').classList.add('oculto');
    }
}

function renderizarBicicletas() {
    const lista = $('#listaBikes');
    if (!lista) return;
    lista.replaceChildren();

    const bikes = state.config?.bikes || [];
    const emUso = new Set(
        locacoesDoUsuario()
            .filter(locacao => locacao.status === 'ATIVA')
            .map(locacao => Number(locacao.bicicletaId))
    );
    const expirada = configExpirada();
    const disponiveis = bikes.filter(bike => !emUso.has(Number(bike.id)));
    const botao = $('#btnGerarCodigo');

    if (!bikes.length) {
        const aviso = document.createElement('p');
        aviso.className = 'mensagem-erro';
        aviso.textContent = 'Nenhuma bicicleta disponível. Gere um novo QR no painel.';
        lista.appendChild(aviso);
        if (botao) botao.disabled = true;
        return;
    }

    let primeiroDisponivel = true;
    bikes.forEach(bike => {
        const usado = emUso.has(Number(bike.id));
        const rotulo = document.createElement('label');
        rotulo.className = 'bike';

        const radio = document.createElement('input');
        radio.type = 'radio';
        radio.name = 'bike';
        radio.value = String(bike.id);
        radio.disabled = usado || expirada;
        if (!usado && !expirada && primeiroDisponivel) {
            radio.checked = true;
            primeiroDisponivel = false;
        }

        const texto = document.createElement('div');
        texto.className = 'bike-texto';
        const codigo = document.createElement('strong');
        codigo.textContent = bike.codigo;
        const modelo = document.createElement('span');
        modelo.textContent = bike.modelo || 'Bicicleta disponível';
        texto.append(codigo, modelo);

        rotulo.append(radio, texto);
        if (usado) {
            const selo = document.createElement('span');
            selo.className = 'bike-selo';
            selo.textContent = 'Em uso';
            rotulo.appendChild(selo);
        }
        lista.appendChild(rotulo);
    });

    if (!disponiveis.length) {
        const aviso = document.createElement('p');
        aviso.className = 'aviso';
        aviso.textContent = 'Todas as bicicletas do catálogo estão em uso. Depois de devolver no painel, leia um novo QR para atualizar o catálogo.';
        lista.appendChild(aviso);
    }

    if (botao) botao.disabled = expirada || disponiveis.length === 0 || Boolean(state.codigoAtual);
}

function atualizarEstimativa() {
    const horas = Math.min(24, Math.max(1, Math.trunc(Number($('#horas').value)) || 1));
    $('#valorEstimado').textContent = formatarMoeda(horas * VALOR_HORA);
}

async function gerarCodigo() {
    if (state.codigoAtual) {
        avisar('Confirme a solicitação atual ou inicie um novo código.', 'erro');
        return;
    }
    if (configExpirada()) {
        avisar('O pareamento expirou. Leia um novo QR no painel.', 'erro');
        return;
    }
    const selecionada = document.querySelector('input[name="bike"]:checked');
    if (!selecionada) {
        avisar('Escolha uma bicicleta disponível.', 'erro');
        return;
    }
    if (!window.crypto || !crypto.subtle) {
        avisar('Este navegador não tem WebCrypto. Abra o app em localhost ou HTTPS.', 'erro');
        return;
    }

    const bike = state.config.bikes.find(item => Number(item.id) === Number(selecionada.value));
    if (!bike) {
        avisar('A bicicleta escolhida não está mais no catálogo.', 'erro');
        return;
    }

    const botao = $('#btnGerarCodigo');
    botao.disabled = true;
    try {
        const horas = Math.min(24, Math.max(1, Math.trunc(Number($('#horas').value)) || 1));
        const retirada = new Date(Math.floor(Date.now() / 60000) * 60000);
        state.solicitacaoAtual = {
            bike: { id: bike.id, codigo: bike.codigo, modelo: bike.modelo || '' },
            horas,
            retirada,
            segredo: state.config.seg,
            pareamentoId: state.config.pid
        };
        const solicitacao = state.solicitacaoAtual;
        const codigo = await montarCodigo(
            state.config.usuario.clienteId,
            Number(bike.id),
            horas,
            retirada);
        if (state.solicitacaoAtual !== solicitacao) return;
        state.codigoAtual = codigo;
        salvarSolicitacao();

        $('#resultadoConfirmacao').classList.add('oculto');
        $('#resultadoConfirmacao').replaceChildren();
        $('#codigoGerado').textContent = state.codigoAtual;
        $('#codigoResumo').textContent = `${bike.codigo} · ${horas}h`;
        $('#areaCodigo').classList.remove('oculto');
        $('#areaConfirmacao').classList.remove('oculto');
        $('#areaCodigo').scrollIntoView({ behavior: 'smooth', block: 'center' });
        avisar('Código gerado. Digite-o no painel.');
    } catch (e) {
        avisar(`Falha ao gerar o código: ${e.message}`, 'erro');
    } finally {
        botao.disabled = configExpirada() || Boolean(state.codigoAtual);
    }
}

async function montarCodigo(clienteId, bicicletaId, horas, retirada) {
    const dados = new Uint8Array(14);
    dados[0] = 2;
    escrever24(dados, 1, clienteId);
    escrever24(dados, 4, bicicletaId);
    escrever32(dados, 7, Math.floor(retirada.getTime() / 60000));
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

function salvarSolicitacao() {
    if (!state.codigoAtual || !state.solicitacaoAtual) return;
    guardarJson(CHAVE_SOLICITACAO, {
        clienteId: state.config.usuario.clienteId,
        codigo: state.codigoAtual,
        solicitacao: {
            ...state.solicitacaoAtual,
            retirada: state.solicitacaoAtual.retirada.toISOString()
        }
    });
}

function restaurarSolicitacao() {
    const salva = lerJson(CHAVE_SOLICITACAO) || lerJson(CHAVE_SOLICITACAO, sessionStorage);
    if (!salva?.codigo || !salva?.solicitacao
        || Number(salva.clienteId) !== Number(state.config?.usuario?.clienteId)) {
        limparSolicitacao();
        return;
    }
    state.codigoAtual = salva.codigo;
    state.solicitacaoAtual = {
        segredo: state.config.seg,
        pareamentoId: state.config.pid,
        ...salva.solicitacao,
        retirada: new Date(salva.solicitacao.retirada)
    };
    $('#codigoGerado').textContent = state.codigoAtual;
    $('#codigoResumo').textContent =
        `${state.solicitacaoAtual.bike.codigo} · ${state.solicitacaoAtual.horas}h`;
}

function limparSolicitacao() {
    state.codigoAtual = null;
    state.solicitacaoAtual = null;
    try {
        localStorage.removeItem(CHAVE_SOLICITACAO);
        sessionStorage.removeItem(CHAVE_SOLICITACAO);
    } catch { /* ignore */ }
    $('#codigoGerado').textContent = '';
    $('#codigoResumo').textContent = '';
    $('#areaCodigo').classList.add('oculto');
    $('#areaConfirmacao').classList.add('oculto');
    $('#confirmacao').value = '';
    mostrarErro('#erroConfirmacao', '');
}

function resetAlugar() {
    limparSolicitacao();
    const resultado = $('#resultadoConfirmacao');
    if (resultado) {
        resultado.classList.add('oculto');
        resultado.replaceChildren();
    }
    const horas = $('#horas');
    if (horas) horas.value = '2';
    atualizarEstimativa();
    renderizarBicicletas();
}

function tamanhoAssinatura() {
    // A versão 1 começa com 04 a 07 em Base32.
    return /^0[4-7]/.test(state.codigoAtual || '') ? 4 : 8;
}

async function confirmarLocacao(evento) {
    evento.preventDefault();
    mostrarErro('#erroConfirmacao', '');

    const tamanho = tamanhoAssinatura();
    const valor = mascaras.formatar.confirmacao($('#confirmacao').value, tamanho);
    const partes = valor.match(/^L([1-9]\d*)-([0-9A-Z]{4}|[0-9A-Z]{8})$/);
    if (!partes) {
        mostrarErro('#erroConfirmacao', 'Confira o código de confirmação exibido no painel.');
        return;
    }
    if (!window.crypto || !crypto.subtle) {
        mostrarErro('#erroConfirmacao', 'WebCrypto indisponível. Abra o app em localhost ou HTTPS.');
        return;
    }
    if (!state.solicitacaoAtual || !state.codigoAtual) {
        mostrarErro('#erroConfirmacao', 'Gere um novo código no aplicativo para vincular a confirmação à bicicleta.');
        return;
    }

    const { codigoAtual, solicitacaoAtual, config } = state;
    const codigo = codigoAtual.replaceAll('-', '');
    const mensagem = tamanho === 4 ? `L${partes[1]}` : `L${partes[1]}:${codigo}`;
    const digest = await hmac(solicitacaoAtual.segredo, new TextEncoder().encode(mensagem));
    if (state.solicitacaoAtual !== solicitacaoAtual || state.config !== config) return;
    const esperado = base32(digest).slice(0, tamanho);
    if (esperado !== partes[2]) {
        mostrarErro('#erroConfirmacao', 'A confirmação não corresponde à solicitação atual.');
        return;
    }

    const registro = salvarLocacaoConfirmada(Number(partes[1]), valor);
    limparSolicitacao();
    renderizarInicio();
    renderizarLocacoes();
    renderizarBicicletas();

    const resultado = $('#resultadoConfirmacao');
    resultado.innerHTML = `
        <div class="linha-entre">
            <h2>Locação #${registro.id} confirmada!</h2>
            <span class="selo ativa">Em andamento</span>
        </div>
        <p class="texto-suave">
            ${esc(registro.codigoBicicleta)} · ${formatarDataHora(registro.retirada)}
            até ${formatarDataHora(registro.previsaoDevolucao)}
        </p>
        <div class="grade-detalhes">
            <div>
                <span class="rotulo">Horas</span>
                <strong>${registro.horas}h</strong>
            </div>
            <div>
                <span class="rotulo">Valor estimado</span>
                <strong>${formatarMoeda(registro.valor)}</strong>
            </div>
            <div>
                <span class="rotulo">Confirmação</span>
                <strong class="texto-mono">${esc(registro.confirmacao)}</strong>
            </div>
        </div>
        <div class="acoes">
            <button class="botao" type="button" data-aba="locacoes">Ver minhas locações</button>
            <button class="botao secundario" type="button" data-acao="nova-locacao">Alugar outra</button>
        </div>
    `;
    resultado.classList.remove('oculto');
    resultado.scrollIntoView({ behavior: 'smooth', block: 'center' });
    avisar(`Locação #${registro.id} confirmada e salva no aplicativo.`);
}

function salvarLocacaoConfirmada(id, confirmacao) {
    const solicitacao = state.solicitacaoAtual;
    const retirada = solicitacao.retirada instanceof Date
        ? solicitacao.retirada
        : new Date(solicitacao.retirada);
    const previsao = new Date(retirada.getTime() + solicitacao.horas * 3600000);
    const oficial = locacoesDoUsuario().find(locacao => locacao.id === id && locacao.dadosDoPainel);
    if (oficial) {
        const registro = { ...oficial, horas: solicitacao.horas, confirmacao };
        salvarLocacao(registro);
        return registro;
    }
    const registro = {
        id,
        clienteId: state.config.usuario.clienteId,
        pareamentoId: solicitacao.pareamentoId || null,
        bicicletaId: solicitacao.bike.id,
        codigoBicicleta: solicitacao.bike.codigo,
        modeloBicicleta: solicitacao.bike.modelo || '',
        horas: solicitacao.horas,
        retirada: retirada.toISOString(),
        previsaoDevolucao: previsao.toISOString(),
        devolucaoEfetiva: null,
        valor: solicitacao.horas * VALOR_HORA,
        status: 'ATIVA',
        confirmacao,
        criadaEm: new Date().toISOString()
    };
    salvarLocacao(registro);
    return registro;
}

/* ============================ minhas locações ============================ */

function definirFiltro(filtro) {
    if (!['todas', 'ativas', 'finalizadas'].includes(filtro)) return;
    state.filtro = filtro;
    $$('#filtrosLocacoes .filtro').forEach(botao => {
        const ativo = botao.dataset.filtro === filtro;
        botao.classList.toggle('ativo', ativo);
        botao.setAttribute('aria-selected', String(ativo));
    });
    renderizarLocacoes();
}

function renderizarLocacoes() {
    const todas = locacoesDoUsuario();
    const filtradas = todas.filter(locacao => {
        if (state.filtro === 'ativas') return locacao.status === 'ATIVA';
        if (state.filtro === 'finalizadas') return locacao.status === 'FINALIZADA';
        return true;
    });

    const lista = $('#listaLocacoes');
    lista.innerHTML = filtradas.map(locacao => {
        const status = statusDe(locacao);
        const valor = valorAtual(locacao);
        const finalizada = locacao.status === 'FINALIZADA';
        const referencia = finalizada ? locacao.devolucaoEfetiva : locacao.previsaoDevolucao;
        return `
            <button class="locacao-item ${status.chave}" type="button" data-locacao="${locacao.id}">
                <span class="locacao-topo">
                    <span class="bike-mini">
                        <span class="bike-mini-icone" aria-hidden="true">🚲</span>
                        <span>
                            <strong>${esc(locacao.codigoBicicleta || 'Bicicleta')}</strong>
                            <span>${esc(locacao.modeloBicicleta || 'Locação')}</span>
                        </span>
                    </span>
                    <span class="selo ${status.chave}">${status.rotulo}</span>
                </span>
                <span class="locacao-meta">
                    <span>${finalizada ? 'Devolvida' : 'Previsão'} ${formatarDataCurta(referencia)}</span>
                    <span>${formatarDuracao(duracaoAtual(locacao))}</span>
                    <strong>${formatarMoeda(valor)}</strong>
                </span>
            </button>
        `;
    }).join('');

    const vazio = $('#vazioLocacoes');
    vazio.classList.toggle('oculto', filtradas.length > 0);

    if (!filtradas.length) {
        const titulo = $('#vazioLocacoesTitulo');
        const texto = $('#vazioLocacoesTexto');
        const acao = $('#vazioLocacoes .botao');
        if (!todas.length) {
            titulo.textContent = 'Nenhuma locação ainda';
            texto.textContent = 'Depois que o painel confirmar sua primeira locação, ela aparece aqui.';
            acao.textContent = 'Fazer primeira locação';
            acao.dataset.aba = 'alugar';
            delete acao.dataset.filtro;
        } else {
            titulo.textContent = state.filtro === 'ativas'
                ? 'Nenhuma locação ativa'
                : 'Nenhuma locação finalizada';
            texto.textContent = state.filtro === 'ativas'
                ? 'Quando você alugar uma bicicleta, ela aparece aqui enquanto estiver em andamento.'
                : 'As locações aparecem aqui depois que a devolução for registrada no painel.';
            acao.textContent = 'Ver todas';
            delete acao.dataset.aba;
            acao.dataset.filtro = 'todas';
        }
    }
}

function abrirDetalhe(id) {
    const locacao = locacoesDoUsuario().find(item => Number(item.id) === Number(id));
    if (!locacao) return;
    const status = statusDe(locacao);
    const finalizada = locacao.status === 'FINALIZADA';

    $('#dlgTitulo').textContent = `Locação #${locacao.id}`;
    $('#dlgStatus').className = `selo ${status.chave}`;
    $('#dlgStatus').textContent = status.rotulo;
    $('#dlgBike').textContent = locacao.codigoBicicleta || 'Bicicleta';
    $('#dlgModelo').textContent = locacao.modeloBicicleta || 'Locação';
    $('#dlgRetirada').textContent = formatarDataHora(locacao.retirada);
    $('#dlgPrevisao').textContent = formatarDataHora(locacao.previsaoDevolucao);
    $('#dlgDuracao').textContent = formatarDuracao(duracaoAtual(locacao));
    $('#dlgRotuloValor').textContent = finalizada && locacao.dadosDoPainel ? 'Valor cobrado' : 'Valor estimado';
    $('#dlgValor').textContent = formatarMoeda(valorAtual(locacao));
    $('#dlgConfirmacao').textContent = locacao.confirmacao || '—';
    $('#dlgAviso').textContent = finalizada
        ? (locacao.dadosDoPainel
            ? 'Devolvida em ' + formatarDataHora(locacao.devolucaoEfetiva)
            : 'Locação finalizada. Leia um novo QR para atualizar o valor da devolução.')
        : 'A devolução é registrada no painel do Locabike. Depois de devolver, leia um novo QR para atualizar o histórico e liberar a bicicleta.';

    const dialogo = $('#dialogLocacao');
    if (!dialogo.open) dialogo.showModal();
}

/* ============================ perfil ============================ */

function renderizarPerfil() {
    const usuario = state.usuario || state.config?.usuario || {};
    const expirada = configExpirada();
    const cripto = Boolean(window.crypto && crypto.subtle);

    $('#perfilNome').textContent = usuario.nome || 'Cliente';
    $('#perfilEmail').textContent = usuario.email || '';
    $('#perfilAvatar').textContent = iniciais(usuario.nome || 'Locabike');
    $('#perfilCliente').textContent = usuario.clienteId
        ? `Cliente #${usuario.clienteId}`
        : 'Cliente';

    const status = $('#perfilStatusPareamento');
    status.className = `selo ${expirada ? 'atrasada' : 'ativa'}`;
    status.textContent = expirada ? 'Expirado' : 'Ativo';
    $('#perfilValidade').textContent = state.config?.exp
        ? formatarDataHora(state.config.exp)
        : 'Sem validade';
    $('#perfilBikes').textContent = state.config?.bikes
        ? `${state.config.bikes.length} no catálogo`
        : '—';
    $('#perfilCripto').textContent = cripto ? 'WebCrypto ativo' : 'Indisponível';
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
    $('#btnImagemQr').addEventListener('click', () => $('#arquivoQr').click());
    $('#arquivoQr').addEventListener('change', lerArquivoQr);
    $('#btnPararCamera').addEventListener('click', pararCamera);
    $('#btnLanterna').addEventListener('click', alternarLanterna);
    $('#btnUsarConfig').addEventListener('click', () => usarConfiguracao($('#textoConfig').value));
    $('#formLogin').addEventListener('submit', entrar);
    $('#btnCadastro').addEventListener('click', () => $('#dialogCadastro').showModal());
    $('#btnOutroPareamento').addEventListener('click', () => {
        pararCamera();
        $('#textoConfig').value = '';
        mostrarErro('#erroPareamento', '');
        mostrarTela('telaPareamento');
    });
    $('#btnVoltarApp').addEventListener('click', () => {
        if (state.usuario) {
            mostrarTela('telaApp');
            irPara(state.aba);
        } else if (state.config) {
            mostrarTela('telaLogin');
            $('#avisoPareamentoLogin').classList.toggle('oculto', !configExpirada());
        }
    });
    $('#btnTopoPerfil').addEventListener('click', () => irPara('perfil'));
    $('#btnSairApp').addEventListener('click', sair);
    $('#btnNovoPareamento').addEventListener('click', () => {
        pararCamera();
        $('#textoConfig').value = '';
        mostrarErro('#erroPareamento', '');
        mostrarTela('telaPareamento');
    });
    $('#horas').addEventListener('input', atualizarEstimativa);
    $('#btnGerarCodigo').addEventListener('click', gerarCodigo);
    $('#btnCopiarCodigo').addEventListener('click', copiarCodigo);
    $('#btnNovoCodigo').addEventListener('click', () => {
        resetAlugar();
        irPara('alugar');
    });
    mascaras.aplicar($('#confirmacao'), tamanhoAssinatura);
    $('#formConfirmacao').addEventListener('submit', confirmarLocacao);
    $('#btnVerAtiva').addEventListener('click', () => {
        if (state.locacaoAtiva) abrirDetalhe(state.locacaoAtiva.id);
    });

    document.addEventListener('click', evento => {
        const aba = evento.target.closest('[data-aba]');
        if (aba && !aba.disabled) {
            evento.preventDefault();
            irPara(aba.dataset.aba);
            return;
        }
        const filtro = evento.target.closest('[data-filtro]');
        if (filtro) {
            evento.preventDefault();
            definirFiltro(filtro.dataset.filtro);
            return;
        }
        const acao = evento.target.closest('[data-acao]');
        if (acao?.dataset.acao === 'nova-locacao') {
            resetAlugar();
            irPara('alugar');
            return;
        }
        const item = evento.target.closest('[data-locacao]');
        if (item) {
            abrirDetalhe(Number(item.dataset.locacao));
        }
    });

    document.addEventListener('visibilitychange', () => {
        if (document.hidden && cameraStream) pararCamera();
    });
}

function atualizarEstadoCripto() {
    const cripto = Boolean(window.crypto && crypto.subtle);
    $('#estadoCripto').textContent = cripto
        ? 'WebCrypto ativo'
        : 'WebCrypto indisponível: use localhost ou HTTPS';
}

function iniciar() {
    ligarEventos();
    atualizarEstadoCripto();

    try { sessionStorage.removeItem(CHAVE_CONFIG_SESSAO_ANTIGA); } catch { /* ignore */ }

    const salvo = lerJson(CHAVE_CONFIG);
    if (salvo) {
        try {
            const dados = validarConfig(salvo);
            state.config = dados;
            aplicarTema(dados.tema);
            $('#loginEmail').value = dados.usuario.email || '';
            $('#avisoPareamentoLogin').classList.toggle('oculto', !configExpirada());
            restaurarSolicitacao();

            const sessao = lerJson(CHAVE_SESSAO);
            if (sessao && Number(sessao.clienteId) === Number(dados.usuario.clienteId)) {
                state.usuario = { ...dados.usuario };
                mostrarTela('telaApp');
                irPara('inicio');
                iniciarRelogio();
                if (configExpirada()) {
                    avisar('Pareamento expirado. Leia um novo QR no painel para alugar.', 'erro');
                }
                return;
            }

            mostrarTela('telaLogin');
            return;
        } catch {
            localStorage.removeItem(CHAVE_CONFIG);
        }
    }

    mostrarTela('telaPareamento');
}

document.addEventListener('DOMContentLoaded', iniciar);
