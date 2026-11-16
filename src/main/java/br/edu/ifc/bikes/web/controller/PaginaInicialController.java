package br.edu.ifc.bikes.web.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entrega a pagina inicial da interface didatica.
 * O restante dos arquivos (CSS/JS) e servido pelo resource handler configurado em WebConfig.
 */
@Controller
public class PaginaInicialController {

    @GetMapping("/")
    public ResponseEntity<Resource> inicio() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/app/index.html"));
    }
}
