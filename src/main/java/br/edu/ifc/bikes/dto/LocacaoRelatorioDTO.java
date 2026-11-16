package br.edu.ifc.bikes.dto;

/**
 * Linha do relatorio de locacoes, ja formatada para o JasperReports.
 * Campos como texto mantem o .jrxml independente de locale e de tipo de data.
 * E uma classe JavaBean (getters) porque o JRBeanCollectionDataSource le as propriedades por reflexao.
 */
public class LocacaoRelatorioDTO {

    private final String nomeCliente;
    private final String codigoBicicleta;
    private final String dataRetirada;
    private final String previsaoDevolucao;
    private final String devolucaoEfetiva;
    private final String duracao;
    private final String valor;
    private final String status;

    public LocacaoRelatorioDTO(String nomeCliente, String codigoBicicleta, String dataRetirada,
                               String previsaoDevolucao, String devolucaoEfetiva, String duracao,
                               String valor, String status) {
        this.nomeCliente = nomeCliente;
        this.codigoBicicleta = codigoBicicleta;
        this.dataRetirada = dataRetirada;
        this.previsaoDevolucao = previsaoDevolucao;
        this.devolucaoEfetiva = devolucaoEfetiva;
        this.duracao = duracao;
        this.valor = valor;
        this.status = status;
    }

    public String getNomeCliente() {
        return nomeCliente;
    }

    public String getCodigoBicicleta() {
        return codigoBicicleta;
    }

    public String getDataRetirada() {
        return dataRetirada;
    }

    public String getPrevisaoDevolucao() {
        return previsaoDevolucao;
    }

    public String getDevolucaoEfetiva() {
        return devolucaoEfetiva;
    }

    public String getDuracao() {
        return duracao;
    }

    public String getValor() {
        return valor;
    }

    public String getStatus() {
        return status;
    }
}
