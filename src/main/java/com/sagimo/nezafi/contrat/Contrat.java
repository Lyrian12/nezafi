package com.sagimo.nezafi.contrat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sagimo.nezafi.echeance.Echeance;
import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contrats")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Contrat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emplacement_id", nullable = false)
    private Emplacement emplacement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "locataire_id", nullable = false)
    private User locataire;

    // Facultatif : certains dossiers physiques repris n'ont pas de date de début connue avec
    // certitude. Absence gérée proprement partout où ces dates sont utilisées (tri, export,
    // affichage — cf. Contrat.dateFin ci-dessous pour l'expiration).
    private LocalDate dateDebut;

    // Facultatif, même raison que dateDebut. Un contrat sans dateFin n'est jamais marqué EXPIRE
    // automatiquement ni compté dans l'alerte d'expiration à 30 jours (cf. ContratStatusService,
    // AdminController.contractsPage) : simplement exclu de ces calculs, sans erreur.
    private LocalDate dateFin;

    @Column(length = 1000)
    private String termes;

    // Activité exercée par le locataire dans cette boutique (ex : "Vente de vêtements",
    // "Restauration rapide") — distincte de la catégorie de l'emplacement (Boutique/Magasin).
    private String activite;

    // Nom sous lequel le client présente sa boutique au public (ex : "Les Tiktokeurs",
    // "ETS WANG") — distinct du nom légal du client (nom/prenom sur son compte).
    private String nomEnseigne;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutContrat statut = StatutContrat.EN_ATTENTE;

    // Montant réellement négocié pour ce contrat, distinct du prix courant de
    // l'emplacement : ce dernier peut changer plus tard sans affecter rétroactivement
    // un contrat déjà signé. Sert aussi de base pour l'échéancier de type LOYER.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montantLoyer;

    // Durée (en mois) que ce montant de loyer couvre. La pratique standard est 12 mois,
    // mais un locataire peut négocier moins et payer cash ou étaler via l'échéancier —
    // aucune valeur par défaut n'est imposée ici.
    @Column(nullable = false)
    private Integer dureeLoyerMois;

    // Facultative : masquée par défaut dans le formulaire (bouton "+" à côté du loyer pour la
    // faire apparaître) — beaucoup de dossiers physiques repris n'en portent aucune trace.
    // montantCaution et dureeCautionMois sont toujours renseignés ensemble ou absents ensemble
    // (cf. AdminController), jamais l'un sans l'autre.
    @Column(precision = 12, scale = 2)
    private BigDecimal montantCaution;

    // Idem loyer : durée de loyer que la caution représente, librement négociable,
    // indépendante de dureeLoyerMois. Facultative, même raison que montantCaution ci-dessus.
    private Integer dureeCautionMois;

    // Obligatoire uniquement quand statut passe à RESILIER (validé côté contrôleur,
    // via l'action dédiée de résiliation — jamais modifiable depuis le formulaire
    // générique d'édition).
    @Column(length = 1000)
    private String motifResiliation;

    private LocalDate datePreavis;

    // La facture de paiement vit désormais dans com.sagimo.nezafi.storage.DocumentJoint
    // (nomEntite="Contrat", entiteId=this.id) — même mécanisme générique que les photos
    // d'emplacement, cf. DocumentJointService.

    // Renouvellement : un contrat renouvelé n'est jamais modifié en place, il donne
    // naissance à un nouveau Contrat qui pointe vers lui via ce champ.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrat_precedent_id")
    private Contrat contratPrecedent;

    @JsonIgnore
    @OneToMany(mappedBy = "contrat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Echeance> echeances = new ArrayList<>();
}
