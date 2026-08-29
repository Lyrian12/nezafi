package com.sagimo.nezafi.storage;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Point d'entrée unique pour attacher/lister/consulter des {@link DocumentJoint} — photos
 * d'emplacement, facture de contrat, et tout futur type de fichier attaché à une entité.
 * Ne connaît rien du métier des entités concernées (même logique que
 * {@link com.sagimo.nezafi.audit.AuditService}) : chaque appelant fournit son propre
 * {@code nomEntite} ("Emplacement", "Contrat"...).
 */
@Service
public class DocumentJointService {

    private final DocumentJointRepository documentJointRepository;
    private final FileStorageService fileStorageService;

    public DocumentJointService(DocumentJointRepository documentJointRepository, FileStorageService fileStorageService) {
        this.documentJointRepository = documentJointRepository;
        this.fileStorageService = fileStorageService;
    }

    /** Attache un fichier de plus à l'entité (n'affecte pas les documents déjà attachés). */
    public DocumentJoint attacher(String nomEntite, Long entiteId, MultipartFile fichier, String sousDossier) throws IOException {
        String chemin = fileStorageService.enregistrer(fichier, sousDossier);
        DocumentJoint document = new DocumentJoint();
        document.setNomEntite(nomEntite);
        document.setEntiteId(entiteId);
        document.setCheminStockage(chemin);
        return documentJointRepository.save(document);
    }

    /**
     * Remplace le document unique de l'entité (cf. facture de contrat : un seul fichier à la
     * fois) — supprime les éventuels documents existants avant d'attacher le nouveau.
     */
    @Transactional
    public DocumentJoint remplacerUnique(String nomEntite, Long entiteId, MultipartFile fichier, String sousDossier)
            throws IOException {
        documentJointRepository.deleteByNomEntiteAndEntiteId(nomEntite, entiteId);
        return attacher(nomEntite, entiteId, fichier, sousDossier);
    }

    public List<DocumentJoint> lister(String nomEntite, Long entiteId) {
        return documentJointRepository.findByNomEntiteAndEntiteIdOrderByAjouteLeDesc(nomEntite, entiteId);
    }

    /** Le document le plus récent de l'entité, utilisé comme vignette de couverture. */
    public Optional<DocumentJoint> premier(String nomEntite, Long entiteId) {
        return lister(nomEntite, entiteId).stream().findFirst();
    }

    public DocumentJoint trouver(Long id) {
        return documentJointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }

    public Resource charger(DocumentJoint document) {
        return fileStorageService.charger(document.getCheminStockage());
    }

    public String nomOriginal(DocumentJoint document) {
        return fileStorageService.nomOriginal(document.getCheminStockage());
    }
}
