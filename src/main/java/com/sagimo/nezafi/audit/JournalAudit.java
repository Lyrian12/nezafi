package com.sagimo.nezafi.audit;

import com.sagimo.nezafi.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Une entrée du journal d'audit : trace une création, modification, suppression
 * ou annulation faite par un admin sur une entité sensible (cf. {@link AuditService}
 * pour la liste des entités et champs réellement audités).
 *
 * {@code ancienneValeur}/{@code nouvelleValeur} contiennent un instantané JSON des
 * seuls champs suivis pour l'entité concernée (pas l'entité entière) — construit et
 * fourni par l'appelant, ce module n'a aucune connaissance des entités métier.
 */
@Entity
@Table(name = "journal_audit")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class JournalAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private User utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeActionAudit typeAction;

    @Column(nullable = false)
    private String nomEntite;

    @Column(nullable = false)
    private Long entiteId;

    @Column(length = 2000)
    private String ancienneValeur;

    @Column(length = 2000)
    private String nouvelleValeur;

    @Column(nullable = false)
    private LocalDateTime dateAction = LocalDateTime.now();
}
