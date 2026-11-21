const mascaras = (() => {
    const digitos = texto => texto.replace(/\D/g, '');
    const alfanumerico = texto => texto.toUpperCase().replace(/[^A-Z0-9]/g, '');
    const agrupar = (texto, tamanho, separador) => {
        const grupos = [];
        for (let i = 0; i < texto.length; i += tamanho) {
            grupos.push(texto.slice(i, i + tamanho));
        }
        return grupos.join(separador);
    };

    const formatos = {
        cpf: texto => digitos(texto).slice(0, 11)
            .replace(/^(\d{3})(\d)/, '$1.$2')
            .replace(/^(\d{3}\.\d{3})(\d)/, '$1.$2')
            .replace(/(\d{3}\.\d{3}\.\d{3})(\d)/, '$1-$2'),
        telefone: texto => {
            const numero = digitos(texto).slice(0, 11);
            if (!numero) return '';
            if (numero.length < 3) return '(' + numero;
            const local = numero.slice(2);
            const prefixo = local.length === 9 ? 5 : 4;
            return '(' + numero.slice(0, 2) + ') '
                + [local.slice(0, prefixo), local.slice(prefixo)].filter(Boolean).join('-');
        },
        cartao: texto => agrupar(digitos(texto).slice(0, 19), 4, ' '),
        bicicleta: texto => texto.toUpperCase().replace(/[^A-Z0-9-]/g, '').slice(0, 20),
        solicitacao: texto => agrupar(alfanumerico(texto)
            .replace(/[IL]/g, '1').replace(/O/g, '0').slice(0, 36), 6, '-'),
        confirmacao: (texto, tamanho = 8) => {
            const codigo = alfanumerico(texto).replace(/^L/, '');
            if (!codigo) return texto.toUpperCase().includes('L') ? 'L' : '';
            // O identificador tem comprimento variável. A assinatura tem tamanho fixo.
            const primeiraLetra = codigo.search(/[A-Z]/);
            const limite = codigo.length - tamanho;
            const inicio = primeiraLetra < 0 ? limite
                : limite > 0 ? Math.min(primeiraLetra, limite) : primeiraLetra;
            if (inicio < 1) return 'L' + codigo;
            return 'L' + codigo.slice(0, inicio) + '-' + codigo.slice(inicio);
        }
    };

    function aplicar(campo, tamanhoConfirmacao) {
        const nome = campo.dataset.mascara;
        const formatar = formatos[nome];
        const significativos = nome === 'bicicleta' ? /[^A-Z0-9-]/gi : /[^A-Z0-9]/gi;
        const atualizar = () => {
            const anterior = campo.value;
            const posicao = campo.selectionStart;
            const novo = formatar(anterior, tamanhoConfirmacao?.());
            if (anterior === novo) return;
            campo.value = novo;
            if (posicao === null || posicao === anterior.length) return;
            const inseriuPrefixo = nome === 'confirmacao' && /^L/i.test(novo) && !/^L/i.test(anterior);
            const quantidade = anterior.slice(0, posicao).replace(significativos, '').length
                + Number(inseriuPrefixo);
            let cursor = 0;
            while (cursor < novo.length && novo.slice(0, cursor).replace(significativos, '').length < quantidade) {
                cursor += 1;
            }
            campo.setSelectionRange(cursor, cursor);
        };

        campo.addEventListener('input', evento => {
            if (!evento.isComposing) atualizar();
        });
        campo.addEventListener('compositionend', atualizar);
        campo.form?.addEventListener('reset', () => queueMicrotask(atualizar));
        atualizar();
    }

    return { aplicar, digitos, formatar: formatos };
})();
