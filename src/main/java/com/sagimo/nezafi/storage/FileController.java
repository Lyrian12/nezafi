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
 * Sert les documents joints "publics" (au sens : visibles par tout utilisateur authentifié,
 * pas réservés aux admins) — en pratique aujourd'hui, uniquement les photos d'emplacement,
 * affichées aussi bien côté admin que côté locataire. Un document attaché à une autre entité
 * (ex. "Contrat", la facture de paiement) n'est jamais servi ici : il reste téléchargeable
 * uniquement depuis les routes {@code /admin/**}, déjà réservées aux admins par
 * {@link com.sagimo.nezafi.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/files/documents")
public class FileController {

    private static final String ENTITE_PUBLIQUE = "Emplacement";

    private final DocumentJointService documentJointService;

    public FileController(DocumentJointService documentJointService) {
        this.documentJointService = documentJointService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> voir(@PathVariable Long id) {
        DocumentJoint document = documentJointService.trouver(id);
        if (!ENTITE_PUBLIQUE.equals(document.getNomEntite())) {
            return ResponseEntity.notFound().build();
        }

        Resource ressource = documentJointService.charger(document);
        MediaType type = MediaTypeFactory.getMediaType(ressource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(ressource);
    }
}
