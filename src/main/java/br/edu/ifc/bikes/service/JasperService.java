package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.dto.LocacaoRelatorioDTO;
import br.edu.ifc.bikes.entity.Locacao;
import br.edu.ifc.bikes.repository.LocacaoRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carrega o relatorio de locacoes e gera o PDF.
 * <p>
 * O fonte .jrxml fica versionado e e compilado para .jasper no build (target/classes/reports).
 * Se o .jasper nao estiver disponivel (por exemplo, rodando pela IDE sem passar pelo Maven),
 * o .jrxml e compilado em runtime. Em nenhum dos casos e preciso o Jaspersoft Studio.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class JasperService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String CAMINHO_JASPER = "reports/locacoes.jasper";
    private static final String CAMINHO_JRXML = "reports/locacoes.jrxml";

    private final LocacaoRepository locacaoRepository;
    private JasperReport relatorioLocacoes;

    @PostConstruct
    void carregarRelatorio() {
        try {
            ClassPathResource compilado = new ClassPathResource(CAMINHO_JASPER);
            if (compilado.exists()) {
                try (InputStream entrada = compilado.getInputStream()) {
                    this.relatorioLocacoes = (JasperReport) JRLoader.loadObject(entrada);
                    log.info("Relatorio de locacoes carregado de {}", CAMINHO_JASPER);
                    return;
                }
            }
            try (InputStream fonte = new ClassPathResource(CAMINHO_JRXML).getInputStream()) {
                this.relatorioLocacoes = JasperCompileManager.compileReport(fonte);
                log.info("Relatorio de locacoes compilado em runtime a partir de {}", CAMINHO_JRXML);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Nao foi possivel carregar o relatorio de locacoes", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioLocacoes() {
        List<LocacaoRelatorioDTO> dados = locacaoRepository.findAllByOrderByDataRetiradaDesc()
                .stream()
                .map(this::toLinha)
                .toList();

        // O Jasper grava o datasource dentro deste mapa durante o fill, por isso ele precisa ser mutavel.
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("GERADO_EM", LocalDateTime.now().format(DATA_HORA));

        try {
            JasperPrint relatorio = JasperFillManager.fillReport(
                    relatorioLocacoes, parametros, new JRBeanCollectionDataSource(dados));
            return JasperExportManager.exportReportToPdf(relatorio);
        } catch (JRException ex) {
            throw new IllegalStateException("Erro ao gerar o PDF do relatorio de locacoes", ex);
        }
    }

    private LocacaoRelatorioDTO toLinha(Locacao locacao) {
        Long minutos = locacao.getDevolucaoEfetiva() == null
                ? null
                : Math.max(0, java.time.Duration.between(locacao.getDataRetirada(), locacao.getDevolucaoEfetiva()).toMinutes());

        return new LocacaoRelatorioDTO(
                locacao.getCliente().getNome(),
                locacao.getBicicleta().getCodigo(),
                locacao.getDataRetirada().format(DATA_HORA),
                locacao.getPrevisaoDevolucao().format(DATA_HORA),
                locacao.getDevolucaoEfetiva() == null ? "Em andamento" : locacao.getDevolucaoEfetiva().format(DATA_HORA),
                formatarDuracao(minutos),
                formatarValor(locacao.getValor()),
                locacao.getStatus() == Locacao.Status.ATIVA ? "Ativa" : "Finalizada"
        );
    }

    private String formatarDuracao(Long minutos) {
        if (minutos == null) {
            return "Em andamento";
        }
        long horas = minutos / 60;
        long restantes = minutos % 60;
        if (horas == 0) {
            return restantes + "min";
        }
        return restantes == 0 ? horas + "h" : horas + "h " + restantes + "min";
    }

    private String formatarValor(java.math.BigDecimal valor) {
        return valor == null ? "-" : "R$ " + valor.setScale(2).toPlainString().replace('.', ',');
    }
}
