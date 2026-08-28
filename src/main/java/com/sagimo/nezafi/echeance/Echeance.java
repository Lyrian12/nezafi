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

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montantDu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutEcheance statut = StatutEcheance.EN_ATTENTE;

    @JsonIgnore
    @OneToMany(mappedBy = "echeance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Paiement> paiements = new ArrayList<>();
}
