package com.sagimo.nezafi.paiement;

import com.sagimo.nezafi.echeance.Echeance;
import com.sagimo.nezafi.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un paiement en espèces enregistré pour une {@link Echeance}. Une échéance peut
 * recevoir plusieurs paiements successifs (paiements partiels qui s'accumulent).
 */
@Entity
@Table(name = "paiements")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Paiement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "echeance_id", nullable = false)
    private Echeance echeance;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montantPaye;

    @Column(nullable = false)
    private LocalDate datePaiement;

    // Chemin/URL du scan ou de la photo du reçu — encore un simple champ texte pour l'instant,
    // pas migré vers com.sagimo.nezafi.storage.DocumentJoint (utilisé lui pour les photos
    // d'emplacement et la facture de contrat).
    private String cheminRecu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_enregistrant_id", nullable = false)
    private User adminEnregistrant;
}
