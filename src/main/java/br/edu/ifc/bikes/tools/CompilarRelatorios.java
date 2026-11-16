package br.edu.ifc.bikes.tools;

import net.sf.jasperreports.engine.JasperCompileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Compila os relatorios .jrxml para .jasper durante o build (fase process-classes).
 * <p>
 * O .jrxml continua sendo o fonte versionado. O .jasper gerado vai para target/classes/reports
 * e e empacotado no jar, evitando depender do compilador Java em tempo de execucao.
 * Se o .jasper nao existir (por exemplo, rodando pela IDE), o JasperService compila o .jrxml em runtime.
 */
public final class CompilarRelatorios {

    private CompilarRelatorios() {
    }

    public static void main(String[] args) throws Exception {
        Path origem = Path.of("src/main/resources/reports");
        Path destino = Path.of("target/classes/reports");

        List<Path> fontes;
        try (var arquivos = Files.list(origem)) {
            fontes = arquivos.filter(p -> p.getFileName().toString().endsWith(".jrxml")).toList();
        }
        if (fontes.isEmpty()) {
            throw new IllegalStateException("Nenhum .jrxml encontrado em " + origem);
        }

        Files.createDirectories(destino);
        for (Path fonte : fontes) {
            String nome = fonte.getFileName().toString().replaceFirst("\\.jrxml$", "");
            Path compilado = destino.resolve(nome + ".jasper");
            JasperCompileManager.compileReportToFile(fonte.toString(), compilado.toString());
            System.out.println("[relatorios] " + fonte.getFileName() + " -> " + compilado);
        }
    }
}
