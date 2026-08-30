package com.sagimo.nezafi.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    // Par défaut (pas de ?telecharger=true) : Content-Disposition inline — indispensable pour
    // que <img th:src="@{/files/documents/{id}}"> continue de fonctionner comme aperçu (le
    // navigateur affiche l'image au lieu de la télécharger). ?telecharger=true force
    // l'enregistrement (attachment), pour le bouton "Télécharger" à côté de "Aperçu".
    @GetMapping("/{id}")
    public ResponseEntity<Resource> voir(@PathVariable Long id,
                                          @RequestParam(required = false, defaultValue = "false") boolean telecharger) {
        DocumentJoint document = documentJointService.trouver(id);
        if (!ENTITE_PUBLIQUE.equals(document.getNomEntite())) {
            return ResponseEntity.notFound().build();
        }

        Resource ressource = documentJointService.charger(document);
        MediaType type = MediaTypeFactory.getMediaType(ressource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        String nomAffiche = documentJointService.nomOriginal(document);
        String disposition = (telecharger ? "attachment" : "inline") + "; filename*=UTF-8''"
                + URLEncoder.encode(nomAffiche, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(ressource);
    }
}
