package com.sagimo.nezafi.storage;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un fichier attaché à une entité métier (photo d'emplacement, facture de contrat...).
 * Volontairement générique — comme {@link com.sagimo.nezafi.audit.JournalAudit} — pour servir
 * un même mécanisme à plusieurs types d'entités sans dupliquer la logique de stockage :
 * {@code nomEntite} + {@code entiteId} identifient l'entité propriétaire ("Emplacement",
 * "Contrat"...), sans relation JPA directe pour rester générique.
 *
 * Pas de champ "type de document" pour l'instant : à ce stade, nomEntite suffit à distinguer
 * une photo d'emplacement d'une facture de contrat. À réévaluer si une même entité a un jour
 * besoin de plusieurs catégories de documents.
 */
@Entity
@Table(name = "documents_joints")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DocumentJoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomEntite;

    @Column(nullable = false)
    private Long entiteId;

    // Chemin relatif tel que renvoyé par FileStorageService.enregistrer(...).
    @Column(nullable = false)
    private String cheminStockage;

    @Column(nullable = false)
    private LocalDateTime ajouteLe = LocalDateTime.now();
}
