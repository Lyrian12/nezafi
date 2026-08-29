package com.sagimo.nezafi.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Sert les photos d'emplacements uploadées (cf. {@link FileStorageService}). Accessible à tout
 * utilisateur authentifié (admin ou locataire) via la règle {@code anyRequest().authenticated()}
 * de {@link com.sagimo.nezafi.config.SecurityConfig} : ces photos sont affichées aussi bien côté
 * admin que côté locataire. Les factures de contrat, elles, restent servies depuis
 * {@code com.sagimo.nezafi.admin.AdminController} (sous {@code /admin/**}, donc déjà réservées
 * aux admins).
 */
@RestController
@RequestMapping("/files/emplacements")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{nomFichier:.+}")
    public ResponseEntity<Resource> image(@PathVariable String nomFichier) {
        Resource ressource = fileStorageService.charger("emplacements/" + nomFichier);
        MediaType type = MediaTypeFactory.getMediaType(ressource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(ressource);
    }
}
