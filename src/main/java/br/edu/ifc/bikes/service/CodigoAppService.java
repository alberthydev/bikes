package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.exception.CodigoAppInvalidoException;
import br.edu.ifc.bikes.service.PareamentoService.Pareamento;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Codigo assinado que o aplicativo gera offline e o painel importa.
 *
 * Formato: 14 bytes de dados + 8 bytes de HMAC-SHA256 truncado, escritos em
 * Base32 Crockford (36 caracteres em 6 grupos de 6). O segredo vem do QR de
 * pareamento; sem ele o codigo nao pode ser forjado.
 */
@Service
public class CodigoAppService {

    /** Base32 de Crockford: sem I, L, O e U para evitar confusao na digitacao. */
    private static final String ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int TAMANHO_DADOS = 14;
    private static final int TAMANHO_MAC = 8;
    private static final int TAMANHO_CODIGO = TAMANHO_DADOS + TAMANHO_MAC;
    private static final byte VERSAO = 1;
    /** Tolerancia entre o relogio do celular e o do painel. */
    private static final Duration TOLERANCIA = Duration.ofMinutes(15);
    private static final int HORAS_MAXIMAS = 24;

    private final PareamentoService pareamentoService;
    private final SecureRandom random = new SecureRandom();
    /** Codigos ja usados, com o instante em que podem ser esquecidos. */
    private final Map<String, Instant> usados = new ConcurrentHashMap<>();

    public CodigoAppService(PareamentoService pareamentoService) {
        this.pareamentoService = pareamentoService;
    }

    /**
     * Gera um codigo para testes e para a demonstracao no proprio servidor.
     * O aplicativo faz o mesmo calculo em JavaScript.
     */
    public String gerar(Pareamento pareamento, Long bicicletaId, LocalDateTime retirada, int horas) {
        if (horas < 1 || horas > HORAS_MAXIMAS) {
            throw new IllegalArgumentException("As horas devem ficar entre 1 e " + HORAS_MAXIMAS);
        }
        long minutos = retirada.atZone(ZoneId.systemDefault()).toEpochSecond() / 60;
        byte[] dados = new byte[TAMANHO_DADOS];
        dados[0] = VERSAO;
        escrever24(dados, 1, pareamento.clienteId());
        escrever24(dados, 4, bicicletaId);
        escrever32(dados, 7, minutos);
        dados[11] = (byte) horas;
        int nonce = random.nextInt(0x10000);
        dados[12] = (byte) (nonce >>> 8);
        dados[13] = (byte) nonce;

        byte[] mac = hmac(pareamento.segredo(), dados);
        byte[] codigo = new byte[TAMANHO_CODIGO];
        System.arraycopy(dados, 0, codigo, 0, TAMANHO_DADOS);
        System.arraycopy(mac, 0, codigo, TAMANHO_DADOS, TAMANHO_MAC);
        return formatar(base32(codigo));
    }

    /**
     * Le e valida o codigo digitado. Testa o segredo de cada pareamento ativo
     * e recusa repeticao, adulteracao e codigo vencido.
     */
    public CodigoDecodificado validar(String codigoDigitado) {
        String codigo = normalizar(codigoDigitado);
        if (codigo.isEmpty()) {
            throw new CodigoAppInvalidoException("Informe o código gerado pelo aplicativo.");
        }

        byte[] bytes;
        try {
            bytes = base32Decode(codigo);
        } catch (IllegalArgumentException ex) {
            throw new CodigoAppInvalidoException("O código informado não é válido.");
        }
        if (bytes.length != TAMANHO_CODIGO) {
            throw new CodigoAppInvalidoException("O código informado não é válido.");
        }

        byte[] dados = Arrays.copyOfRange(bytes, 0, TAMANHO_DADOS);
        byte[] mac = Arrays.copyOfRange(bytes, TAMANHO_DADOS, TAMANHO_CODIGO);

        for (Pareamento pareamento : pareamentoService.ativos()) {
            if (!MessageDigest.isEqual(mac, Arrays.copyOf(hmac(pareamento.segredo(), dados), TAMANHO_MAC))) {
                continue;
            }
            return interpretar(codigo, pareamento, dados);
        }
        throw new CodigoAppInvalidoException("Código inválido ou vencido. Gere um novo código no aplicativo.");
    }

    /**
     * Confirmacao curta que o painel devolve e o aplicativo valida com o mesmo
     * segredo: L&lt;id&gt;-XXXX.
     */
    public String gerarConfirmacao(Long locacaoId, String segredo) {
        byte[] mac = hmac(segredo, ("L" + locacaoId).getBytes(StandardCharsets.UTF_8));
        return "L" + locacaoId + "-" + base32(mac).substring(0, 4);
    }

    private CodigoDecodificado interpretar(String codigo, Pareamento pareamento, byte[] dados) {
        if (dados[0] != VERSAO) {
            throw new CodigoAppInvalidoException("Versão de código não suportada. Atualize o aplicativo.");
        }

        long clienteId = ler24(dados, 1);
        long bicicletaId = ler24(dados, 4);
        long minutos = ler32(dados, 7);
        int horas = dados[11] & 0xFF;
        LocalDateTime retirada = LocalDateTime.ofInstant(Instant.ofEpochSecond(minutos * 60L), ZoneId.systemDefault());

        if (clienteId != pareamento.clienteId()) {
            throw new CodigoAppInvalidoException("O código pertence a outro cliente.");
        }
        if (horas < 1 || horas > HORAS_MAXIMAS) {
            throw new CodigoAppInvalidoException("Quantidade de horas inválida no código.");
        }
        if (Duration.between(retirada, LocalDateTime.now()).abs().compareTo(TOLERANCIA) > 0) {
            throw new CodigoAppInvalidoException("Código vencido. Gere um novo código no aplicativo.");
        }

        limparUsados();
        Instant expiraEm = Instant.now().plus(TOLERANCIA);
        if (usados.putIfAbsent(codigo, expiraEm) != null) {
            throw new CodigoAppInvalidoException("Este código já foi utilizado. Gere um novo código.");
        }

        return new CodigoDecodificado(
                pareamento.id(), pareamento.segredo(), clienteId, bicicletaId, retirada, horas);
    }

    private void limparUsados() {
        Instant agora = Instant.now();
        usados.entrySet().removeIf(entrada -> entrada.getValue().isBefore(agora));
    }

    /** Codigo ja validado, pronto para virar locacao. */
    public record CodigoDecodificado(
            String pareamentoId,
            String segredo,
            Long clienteId,
            Long bicicletaId,
            LocalDateTime retirada,
            int horas
    ) { }

    static String formatar(String codigo) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < codigo.length(); i += 6) {
            if (!texto.isEmpty()) {
                texto.append('-');
            }
            texto.append(codigo, i, Math.min(i + 6, codigo.length()));
        }
        return texto.toString();
    }

    static String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        String limpo = codigo.toUpperCase().replaceAll("[^0-9A-Z]", "");
        return limpo.replace('I', '1').replace('L', '1').replace('O', '0');
    }

    private static void escrever24(byte[] destino, int posicao, long valor) {
        destino[posicao] = (byte) (valor >>> 16);
        destino[posicao + 1] = (byte) (valor >>> 8);
        destino[posicao + 2] = (byte) valor;
    }

    private static void escrever32(byte[] destino, int posicao, long valor) {
        destino[posicao] = (byte) (valor >>> 24);
        destino[posicao + 1] = (byte) (valor >>> 16);
        destino[posicao + 2] = (byte) (valor >>> 8);
        destino[posicao + 3] = (byte) valor;
    }

    private static long ler24(byte[] origem, int posicao) {
        return ((long) (origem[posicao] & 0xFF) << 16)
                | ((long) (origem[posicao + 1] & 0xFF) << 8)
                | (origem[posicao + 2] & 0xFF);
    }

    private static long ler32(byte[] origem, int posicao) {
        return ((long) (origem[posicao] & 0xFF) << 24)
                | ((long) (origem[posicao + 1] & 0xFF) << 16)
                | ((long) (origem[posicao + 2] & 0xFF) << 8)
                | (origem[posicao + 3] & 0xFF);
    }

    private static byte[] hmac(String segredo, byte[] dados) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(dados);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 indisponivel", ex);
        }
    }

    private static String base32(byte[] dados) {
        StringBuilder texto = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte dado : dados) {
            buffer = (buffer << 8) | (dado & 0xFF);
            bits += 8;
            while (bits >= 5) {
                texto.append(ALFABETO.charAt((buffer >>> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            texto.append(ALFABETO.charAt((buffer << (5 - bits)) & 31));
        }
        return texto.toString();
    }

    private static byte[] base32Decode(String texto) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < texto.length(); i++) {
            int valor = ALFABETO.indexOf(texto.charAt(i));
            if (valor < 0) {
                throw new IllegalArgumentException("Caractere fora do alfabeto: " + texto.charAt(i));
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                saida.write((buffer >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return saida.toByteArray();
    }
}
