/* global fetch, localStorage */

const CHAVE_TOKEN = 'locabike.token';

const state = {
    token: localStorage.getItem(CHAVE_TOKEN),
    usuario: null,
    aba: 'bicicletas',
    statusFiltro: '',
    bicicletas: [],
    locacoes: [],
    log: []
};

const ROTULO_STATUS = {
    DISPONIVEL: 'Disponível',
    ALUGADA: 'Alugada',
    MANUTENCAO: 'Manutenção',
    ATIVA: 'Ativa',
    FINALIZADA: 'Finalizada'
};

const CLASSE_STATUS = {
    DISPONIVEL: 'disponivel',
    ALUGADA: 'alugada',
    MANUTENCAO: 'manutencao',
    ATIVA: 'ativa',
    FINALIZADA: 'finalizada'
};

const $ = (seletor, raiz = document) => raiz.querySelector(seletor);
const $$ = (seletor, raiz = document) => Array.from(raiz.querySelectorAll(seletor));

/* ============================ utilidades ============================ */

function esc(valor) {
    return String(valor ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;');
}

function seloStatus(status) {
    const rotulo = ROTULO_STATUS[status] || status;
    const classe = CLASSE_STATUS[status] || '';
    return `<span class="status ${classe}">${esc(rotulo)}</span>`;
}

function formatarDataHora(iso) {
    if (!iso) return '—';
    const data = new Date(iso);
    return Number.isNaN(data.getTime())
        ? esc(iso)
        : data.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatarMoeda(valor) {
    if (valor === null || valor === undefined) return '—';
    return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

function formatarDuracao(minutos) {
    if (minutos === null || minutos === undefined) return '—';
    const horas = Math.floor(minutos / 60);
    const resto = minutos % 60;
    if (!horas) return `${resto} min`;
    return resto ? `${horas}h ${resto}min` : `${horas}h`;
}

function paraInputLocal(data) {
    const ajustada = new Date(data.getTime() - data.getTimezoneOffset() * 60000);
    return ajustada.toISOString().slice(0, 16);
}

let timerToast = null;
function avisar(mensagem, tipo = 'sucesso') {
    const toast = $('#toast');
    toast.textContent = mensagem;
    toast.className = `toast ${tipo}`;
    clearTimeout(timerToast);
    timerToast = setTimeout(() => toast.classList.add('oculto'), 4200);
}

/* ============================ camada HTTP ============================ */

function registrarChamada(metodo, caminho, status, dados, corpo, ms) {
    state.log.unshift({ metodo, caminho, status, dados, corpo, ms: Math.round(ms) });
    state.log = state.log.slice(0, 40);
    if (state.aba === 'api') renderizarLog();
}

async function api(metodo, caminho, corpo) {
    const inicio = performance.now();
    const cabecalhos = {};
    if (state.token) cabecalhos.Authorization = `Bearer ${state.token}`;
    if (corpo !== undefined) cabecalhos['Content-Type'] = 'application/json';

    const resposta = await fetch(caminho, {
        method: metodo,
        headers: cabecalhos,
        body: corpo === undefined ? undefined : JSON.stringify(corpo)
    });

    const texto = await resposta.text();
    let dados = null;
    if (texto) {
        try {
            dados = JSON.parse(texto);
        } catch {
            dados = texto;
        }
    }

    registrarChamada(metodo, caminho, resposta.status, dados, corpo, performance.now() - inicio);

    if (resposta.status === 401 && state.token) {
        encerrarSessao();
        throw new Error('Sessão expirada. Faça login novamente.');
    }
    if (!resposta.ok) {
        const mensagem = dados && (dados.message || dados.error || dados.title)
            ? (dados.message || dados.error || dados.title)
            : `A API respondeu ${resposta.status}`;
        throw new Error(mensagem);
    }
    return dados;
}

/* ============================ sessão ============================ */

async function carregarUsuario() {
    state.usuario = await api('GET', '/api/v1/auth/me');
    aplicarUsuarioNoTopo();
}

function aplicarUsuarioNoTopo() {
    const { username, role } = state.usuario;
    $('#usuarioNome').textContent = username;
    const selo = $('#usuarioPerfil');
    selo.textContent = role;
    selo.className = `selo ${role === 'ADMIN' ? '' : 'cliente'}`;
    $('#usuario').classList.remove('oculto');
}

function encerrarSessao(mostrarAviso = true) {
    state.token = null;
    state.usuario = null;
    state.bicicletas = [];
    state.locacoes = [];
    localStorage.removeItem(CHAVE_TOKEN);
    $('#usuario').classList.add('oculto');
    $('#telaApp').classList.add('oculto');
    $('#telaLogin').classList.remove('oculto');
    if (mostrarAviso) avisar('Sessão encerrada.', 'sucesso');
}

async function entrar(usuario, senha) {
    const erro = $('#loginErro');
    erro.classList.add('oculto');
    const botao = $('#btnEntrar');
    botao.disabled = true;
    try {
        const dados = await api('POST', '/api/v1/auth', { username: usuario, password: senha });
        state.token = dados.token;
        localStorage.setItem(CHAVE_TOKEN, dados.token);
        await carregarUsuario();
        $('#telaLogin').classList.add('oculto');
        $('#telaApp').classList.remove('oculto');
        irPara(state.usuario.role === 'ADMIN' ? 'bicicletas' : 'locacoes');
        avisar(`Bem-vindo(a), ${state.usuario.username}.`);
    } catch (e) {
        erro.textContent = e.message;
        erro.classList.remove('oculto');
    } finally {
        botao.disabled = false;
    }
}

/* ============================ navegação ============================ */

function irPara(aba) {
    state.aba = aba;
    $$('#abas .aba').forEach(b => b.classList.toggle('ativa', b.dataset.aba === aba));
    $$('.aba-conteudo').forEach(tela => tela.classList.toggle('oculto', tela.dataset.tela !== aba));

    if (aba === 'bicicletas') carregarBicicletas();
    if (aba === 'locacoes') carregarLocacoes();
    if (aba === 'clientes') carregarClientes();
    if (aba === 'api') renderizarLog();
}

/* ============================ bicicletas ============================ */

function ehAdmin() {
    return state.usuario && state.usuario.role === 'ADMIN';
}

async function carregarBicicletas() {
    try {
        const caminho = state.statusFiltro
            ? `/api/v1/bicicletas/status/${state.statusFiltro}`
            : '/api/v1/bicicletas';
        state.bicicletas = await api('GET', caminho);
        renderizarBicicletas();
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

function renderizarBicicletas() {
    const admin = ehAdmin();
    $('#thAcoesBicicleta').classList.toggle('oculto', !admin);
    $('#formBicicletaArea').classList.toggle('oculto', !admin);

    const corpo = $('#corpoBicicletas');
    corpo.innerHTML = state.bicicletas.map(b => `
        <tr>
            <td><strong>${esc(b.codigo)}</strong></td>
            <td>${esc(b.marca)} <span class="sub">${esc(b.modelo)}</span></td>
            <td>${esc(b.cor)}</td>
            <td>${esc(b.aro)}</td>
            <td>${seloStatus(b.status)}</td>
            ${admin ? `
            <td>
                <div class="acoes-linha">
                    <select data-status-id="${b.id}">
                        ${Object.entries(ROTULO_STATUS).filter(([chave]) => ['DISPONIVEL', 'ALUGADA', 'MANUTENCAO'].includes(chave))
                            .map(([chave, rotulo]) => `<option value="${chave}" ${chave === b.status ? 'selected' : ''}>${rotulo}</option>`).join('')}
                    </select>
                    <button class="botao pequeno secundario" type="button" data-acao="atualizar" data-id="${b.id}">Salvar</button>
                    <button class="botao pequeno perigo" type="button" data-acao="excluir" data-id="${b.id}">Excluir</button>
                </div>
            </td>` : ''}
        </tr>
    `).join('');

    $('#vazioBicicletas').classList.toggle('oculto', state.bicicletas.length > 0);
}

async function buscarPorCodigo() {
    const codigo = $('#buscaCodigo').value.trim();
    if (!codigo) {
        carregarBicicletas();
        return;
    }
    try {
        state.bicicletas = [await api('GET', `/api/v1/bicicletas/codigo/${encodeURIComponent(codigo)}`)];
        renderizarBicicletas();
    } catch (e) {
        state.bicicletas = [];
        renderizarBicicletas();
        avisar(e.message, 'erro');
    }
}

async function criarBicicleta(form) {
    const dados = new FormData(form);
    const corpo = {
        codigo: dados.get('codigo'),
        marca: dados.get('marca'),
        modelo: dados.get('modelo'),
        cor: dados.get('cor'),
        aro: Number(dados.get('aro')),
        status: dados.get('status')
    };
    try {
        const criada = await api('POST', '/api/v1/bicicletas', corpo);
        form.reset();
        form.aro.value = 29;
        avisar(`Bicicleta ${criada.codigo} cadastrada.`);
        await carregarBicicletas();
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

async function atualizarStatusBicicleta(id) {
    const bicicleta = state.bicicletas.find(b => b.id === id);
    const select = document.querySelector(`select[data-status-id="${id}"]`);
    if (!bicicleta || !select) return;
    try {
        await api('PUT', `/api/v1/bicicletas/${id}`, {
            codigo: bicicleta.codigo,
            marca: bicicleta.marca,
            modelo: bicicleta.modelo,
            cor: bicicleta.cor,
            aro: bicicleta.aro,
            status: select.value
        });
        avisar(`Status de ${bicicleta.codigo} atualizado.`);
        await carregarBicicletas();
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

async function excluirBicicleta(id) {
    const bicicleta = state.bicicletas.find(b => b.id === id);
    if (!bicicleta) return;
    if (!confirm(`Excluir a bicicleta ${bicicleta.codigo}?`)) return;
    try {
        await api('DELETE', `/api/v1/bicicletas/${id}`);
        avisar(`Bicicleta ${bicicleta.codigo} excluída.`);
        await carregarBicicletas();
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

/* ============================ locações ============================ */

async function carregarLocacoes() {
    const admin = ehAdmin();
    $('#areaRelatorio').classList.toggle('oculto', !admin);
    $('#formLocacao').classList.toggle('oculto', admin || !state.usuario.clienteId);
    $('#tituloLocacoes').textContent = admin ? 'Todas as locações' : 'Minhas locações';
    $('#thClienteLocacao').classList.toggle('oculto', !admin);

    try {
        state.locacoes = await api('GET', admin ? '/api/v1/locacoes' : '/api/v1/locacoes/minhas');
        renderizarLocacoes();
        if (!admin) await carregarSelectBicicletas();
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

async function carregarSelectBicicletas() {
    const select = $('#selectBicicletas');
    const previsao = $('input[name="previsaoDevolucao"]', $('#formLocacao'));
    if (previsao && !previsao.value) {
        previsao.value = paraInputLocal(new Date(Date.now() + 2 * 60 * 60 * 1000));
    }
    try {
        const disponiveis = await api('GET', '/api/v1/bicicletas/status/DISPONIVEL');
        select.innerHTML = disponiveis.length
            ? disponiveis.map(b => `<option value="${b.id}">${esc(b.codigo)} · ${esc(b.marca)} ${esc(b.modelo)}</option>`).join('')
            : '<option value="">Nenhuma bicicleta disponível</option>';
    } catch (e) {
        select.innerHTML = '<option value="">Erro ao carregar bicicletas</option>';
        avisar(e.message, 'erro');
    }
}

function renderizarLocacoes() {
    const admin = ehAdmin();
    const corpo = $('#corpoLocacoes');
    corpo.innerHTML = state.locacoes.map(l => `
        <tr>
            <td>${l.id}</td>
            <td class="${admin ? '' : 'oculto'}">${esc(l.nomeCliente)}</td>
            <td><strong>${esc(l.codigoBicicleta)}</strong></td>
            <td>${formatarDataHora(l.dataRetirada)}</td>
            <td>${formatarDataHora(l.previsaoDevolucao)}</td>
            <td>${formatarDataHora(l.devolucaoEfetiva)}</td>
            <td>${formatarDuracao(l.duracaoMinutos)}</td>
            <td>${formatarMoeda(l.valor)}</td>
            <td>${seloStatus(l.status)}</td>
            <td>
                ${l.status === 'ATIVA' ? `
                <div class="acoes-linha">
                    <label class="opcao-problema" title="Marque se a bicicleta voltou com problema">
                        <input type="checkbox" data-problema="${l.id}"> com problema
                    </label>
                    <button class="botao pequeno" type="button" data-acao="devolver" data-id="${l.id}">Registrar devolução</button>
                </div>` : '—'}
            </td>
        </tr>
    `).join('');

    $('#vazioLocacoes').classList.toggle('oculto', state.locacoes.length > 0);
}

async function solicitarLocacao(form) {
    const dados = new FormData(form);
    const bicicletaId = dados.get('bicicletaId');
    if (!bicicletaId) {
        avisar('Nenhuma bicicleta disponível para alugar.', 'erro');
        return;
    }
    try {
        const locacao = await api('POST', '/api/v1/locacoes', {
            bicicletaId: Number(bicicletaId),
            previsaoDevolucao: dados.get('previsaoDevolucao'),
            numeroCartao: dados.get('numeroCartao')
        });
        avisar(`Locação #${locacao.id} criada para ${locacao.codigoBicicleta}.`);
        await carregarLocacoes();
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

async function devolverLocacao(id) {
    const caixa = document.querySelector(`input[data-problema="${id}"]`);
    const problema = Boolean(caixa && caixa.checked);
    try {
        const locacao = await api('POST', `/api/v1/locacoes/${id}/devolucao`, { problema });
        avisar(`Locação #${id} finalizada. Valor: ${formatarMoeda(locacao.valor)}.`);
        await carregarLocacoes();
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

async function baixarRelatorio() {
    const inicio = performance.now();
    const janela = window.open('', '_blank');
    try {
        const resposta = await fetch('/api/v1/locacoes/relatorio', {
            headers: { Authorization: `Bearer ${state.token}` }
        });
        if (!resposta.ok) {
            const texto = await resposta.text();
            let mensagem = `A API respondeu ${resposta.status}`;
            try {
                const json = JSON.parse(texto);
                mensagem = json.message || mensagem;
            } catch { /* corpo não JSON */ }
            registrarChamada('GET', '/api/v1/locacoes/relatorio', resposta.status, texto, undefined, performance.now() - inicio);
            if (janela) janela.close();
            throw new Error(mensagem);
        }
        const blob = await resposta.blob();
        registrarChamada('GET', '/api/v1/locacoes/relatorio', resposta.status,
            `PDF gerado (${(blob.size / 1024).toFixed(1)} KB)`, undefined, performance.now() - inicio);

        const url = URL.createObjectURL(blob);
        if (janela) {
            janela.location.href = url;
        } else {
            window.open(url, '_blank');
        }
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
        avisar('Relatório gerado em PDF.');
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

/* ============================ clientes ============================ */

async function carregarClientes() {
    const admin = ehAdmin();
    const corpo = $('#corpoClientes');

    try {
        if (admin) {
            const clientes = await api('GET', '/api/v1/clientes');
            corpo.innerHTML = clientes.map(c => `
                <tr>
                    <td>${c.id}</td>
                    <td><strong>${esc(c.nome)}</strong></td>
                    <td>${esc(c.cpf)}</td>
                    <td>${esc(c.telefone || '—')}</td>
                    <td>${esc(c.email || '—')}</td>
                    <td>${esc(c.usuarioId)}</td>
                </tr>
            `).join('');
            $('#vazioClientes').classList.toggle('oculto', clientes.length > 0);
            $('#areaNovoCliente').classList.add('oculto');
        } else if (state.usuario.clienteId) {
            const cliente = await api('GET', `/api/v1/clientes/${state.usuario.clienteId}`);
            corpo.innerHTML = `
                <tr>
                    <td>${cliente.id}</td>
                    <td><strong>${esc(cliente.nome)}</strong></td>
                    <td>${esc(cliente.cpf)}</td>
                    <td>${esc(cliente.telefone || '—')}</td>
                    <td>${esc(cliente.email || '—')}</td>
                    <td>${esc(cliente.usuarioId)}</td>
                </tr>`;
            $('#vazioClientes').classList.add('oculto');
            $('#areaNovoCliente').classList.add('oculto');
        } else {
            corpo.innerHTML = '';
            $('#vazioClientes').classList.remove('oculto');
            $('#vazioClientes').textContent = 'Seu usuário ainda não possui cadastro de cliente.';
            $('#areaNovoCliente').classList.remove('oculto');
        }
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

async function criarCliente(form) {
    const dados = new FormData(form);
    try {
        const cliente = await api('POST', '/api/v1/clientes', {
            nome: dados.get('nome'),
            cpf: dados.get('cpf'),
            telefone: dados.get('telefone') || null,
            email: dados.get('email') || null
        });
        form.reset();
        state.usuario.clienteId = cliente.id;
        avisar('Cadastro de cliente concluído. Agora você pode alugar bicicletas.');
        await carregarClientes();
    } catch (e) {
        avisar(e.message, 'erro');
    }
}

/* ============================ log ============================ */

function renderizarLog() {
    const lista = $('#listaLog');
    if (!state.log.length) {
        lista.innerHTML = '<p class="vazio">Nenhuma chamada registrada ainda.</p>';
        return;
    }
    lista.innerHTML = state.log.map(entrada => {
        const ok = entrada.status >= 200 && entrada.status < 400;
        const detalhes = [];
        if (entrada.corpo !== undefined && entrada.corpo !== null) {
            detalhes.push(`→ ${resumir(entrada.corpo)}`);
        }
        if (entrada.dados !== undefined && entrada.dados !== null && entrada.dados !== '') {
            detalhes.push(`← ${resumir(entrada.dados)}`);
        }
        return `
        <article class="log-item ${ok ? 'ok' : 'erro'}">
            <div class="log-topo">
                <span class="log-metodo ${entrada.metodo.toLowerCase()}">${esc(entrada.metodo)}</span>
                <span class="log-caminho">${esc(entrada.caminho)}</span>
                <span class="log-status">${entrada.status}</span>
                <span class="log-tempo">${entrada.ms} ms</span>
            </div>
            ${detalhes.length ? `<div class="log-detalhe">${esc(detalhes.join('\n'))}</div>` : ''}
        </article>`;
    }).join('');
}

function resumir(valor) {
    const texto = typeof valor === 'string' ? valor : JSON.stringify(valor, null, 2);
    return texto.length > 1200 ? `${texto.slice(0, 1200)}\n…` : texto;
}

/* ============================ eventos ============================ */

function ligarEventos() {
    $('#formLogin').addEventListener('submit', evento => {
        evento.preventDefault();
        entrar($('#loginUsuario').value.trim(), $('#loginSenha').value);
    });

    $$('[data-conta]').forEach(botao => {
        botao.addEventListener('click', () => {
            $('#loginUsuario').value = botao.dataset.conta;
            $('#loginSenha').value = '123456';
        });
    });

    $('#btnSair').addEventListener('click', () => encerrarSessao());

    $$('#abas .aba').forEach(botao => {
        botao.addEventListener('click', () => irPara(botao.dataset.aba));
    });
    $$('[data-aba]').forEach(botao => {
        if (botao.classList.contains('aba')) return;
        botao.addEventListener('click', () => irPara(botao.dataset.aba));
    });

    $('#btnBuscarCodigo').addEventListener('click', buscarPorCodigo);
    $('#buscaCodigo').addEventListener('keydown', evento => {
        if (evento.key === 'Enter') {
            evento.preventDefault();
            buscarPorCodigo();
        }
    });

    $('#filtrosStatus').addEventListener('click', evento => {
        const botao = evento.target.closest('.filtro');
        if (!botao) return;
        state.statusFiltro = botao.dataset.status;
        $$('#filtrosStatus .filtro').forEach(b => b.classList.toggle('ativo', b === botao));
        $('#buscaCodigo').value = '';
        carregarBicicletas();
    });

    $('#corpoBicicletas').addEventListener('click', evento => {
        const botao = evento.target.closest('button[data-acao]');
        if (!botao) return;
        const id = Number(botao.dataset.id);
        if (botao.dataset.acao === 'atualizar') atualizarStatusBicicleta(id);
        if (botao.dataset.acao === 'excluir') excluirBicicleta(id);
    });

    $('#formBicicleta').addEventListener('submit', evento => {
        evento.preventDefault();
        criarBicicleta(evento.target);
    });

    $('#formLocacao').addEventListener('submit', evento => {
        evento.preventDefault();
        solicitarLocacao(evento.target);
    });

    $('#corpoLocacoes').addEventListener('click', evento => {
        const botao = evento.target.closest('button[data-acao="devolver"]');
        if (botao) devolverLocacao(Number(botao.dataset.id));
    });

    $('#btnRelatorio').addEventListener('click', baixarRelatorio);

    $('#formCliente').addEventListener('submit', evento => {
        evento.preventDefault();
        criarCliente(evento.target);
    });

    $('#btnLimparLog').addEventListener('click', () => {
        state.log = [];
        renderizarLog();
    });
}

async function iniciar() {
    ligarEventos();
    if (!state.token) return;

    try {
        await carregarUsuario();
        $('#telaLogin').classList.add('oculto');
        $('#telaApp').classList.remove('oculto');
        irPara(state.usuario.role === 'ADMIN' ? 'bicicletas' : 'locacoes');
    } catch {
        encerrarSessao(false);
    }
}

document.addEventListener('DOMContentLoaded', iniciar);
