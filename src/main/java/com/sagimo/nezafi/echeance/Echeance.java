package com.sagimo.nezafi.echeance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.paiement.Paiement;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Une échéance de paiement pour un {@link Contrat}. Les échéances ne sont jamais
 * générées automatiquement selon une périodicité (les loyers ne sont pas mensuels
 * chez Nezafi : chaque contrat a son propre échéancier négocié au cas par cas) —
 * elles sont toujours saisies manuellement par l'admin.
 */
@Entity
@Table(name = "echeances")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Echeance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrat_id", nullable = false)
    private Contrat contrat;

    @Column(nullable = false)
    private LocalDate dateEcheance;

    // Facultatif, même raison que le prix d'un emplacement ou la caution d'un contrat :
    // certains dossiers physiques repris n'ont pas ce montant connu avec certitude à la saisie.
    // Sans montant, une échéance ne peut jamais être marquée PAYEE automatiquement (rien à
    // comparer un paiement) — cf. EcheanceStatusService.recalculerStatut — mais reste
    // normalement visible et modifiable (montant renseignable plus tard).
    @Column(precision = 12, scale = 2)
    private BigDecimal montantDu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutEcheance statut = StatutEcheance.EN_COURS;

    // Défaut LOYER : rétro-compatible avec les échéances déjà saisies avant l'introduction
    // de la caution (elles étaient toutes, de fait, des échéances de loyer).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeEcheance type = TypeEcheance.LOYER;

    @JsonIgnore
    @OneToMany(mappedBy = "echeance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Paiement> paiements = new ArrayList<>();
}
