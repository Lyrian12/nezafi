package com.sagimo.nezafi.contrat;

import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.emplacement.StatutEmplacement;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Synchronise le statut d'un {@link Emplacement} avec celui de son {@link Contrat}.
 *
 * Règles appliquées :
 * <ul>
 *     <li>Contrat {@code VALIDER} (et non expiré) → emplacement {@code NON_DISPONIBLE}.</li>
 *     <li>Contrat {@code RESILIER}, {@code REJETER}, ou dont la {@code dateFin} est dépassée
 *         → emplacement {@code DISPONIBLE}, <strong>sauf</strong> si il est actuellement
 *         {@code EN_MAINTENANCE} : ce statut prime et n'est jamais écrasé automatiquement.</li>
 *     <li>Contrat {@code EN_ATTENTE} (et non expiré) → aucune action, le statut de
 *         l'emplacement n'est pas modifié.</li>
 * </ul>
 *
 * {@link #syncEmplacementStatut(Contrat)} est appelée juste après la sauvegarde de tout contrat
 * (création, édition, résiliation, suppression — cf. AdminController) pour synchroniser
 * l'emplacement à chaque action explicite. {@link #rafraichirStatut(Emplacement)} comble le trou
 * laissé par une expiration silencieuse (aucune action admin) : recalcul paresseux, même
 * principe que {@link #verifierExpiration(Contrat)} ci-dessous pour le statut du contrat
 * lui-même.
 */
@Service
public class ContratStatusService {

    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;

    public ContratStatusService(EmplacementRepository emplacementRepository, ContratRepository contratRepository) {
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
    }

    /**
     * Applique les règles de synchronisation ci-dessus pour le contrat donné.
     * Ne fait rien si le contrat n'est rattaché à aucun emplacement.
     */
    public void syncEmplacementStatut(Contrat contrat) {
        Emplacement emplacement = contrat.getEmplacement();
        if (emplacement == null) {
            return;
        }

        boolean expire = contrat.getDateFin() != null && contrat.getDateFin().isBefore(LocalDate.now());
        boolean doitLibererLEmplacement = expire
                || contrat.getStatut() == StatutContrat.RESILIER
                || contrat.getStatut() == StatutContrat.REJETER;

        if (doitLibererLEmplacement) {
            libererEmplacement(emplacement);
            return;
        }

        if (contrat.getStatut() == StatutContrat.VALIDER) {
            emplacement.setStatut(StatutEmplacement.NON_DISPONIBLE);
            emplacementRepository.save(emplacement);
        }
        // StatutContrat.EN_ATTENTE (et non expiré) : comportement volontairement neutre,
        // le statut de l'emplacement n'est pas modifié tant que le contrat n'est pas validé.
    }

    /**
     * Remet un emplacement à DISPONIBLE, sauf s'il est actuellement EN_MAINTENANCE
     * (ce statut prime toujours sur la libération automatique).
     *
     * Utilisée en interne par {@link #syncEmplacementStatut(Contrat)}, et directement par la
     * suppression d'un contrat : un contrat supprimé ne pilote plus l'emplacement, qui doit
     * donc être libéré sans dépendre de son ancien statut.
     */
    public void libererEmplacement(Emplacement emplacement) {
        if (emplacement == null || emplacement.getStatut() == StatutEmplacement.EN_MAINTENANCE) {
            return;
        }
        emplacement.setStatut(StatutEmplacement.DISPONIBLE);
        emplacementRepository.save(emplacement);
    }

    /**
     * Recalcule paresseusement le statut d'un emplacement à partir de ses contrats actuels —
     * même pattern que {@link com.sagimo.nezafi.echeance.EcheanceStatusService} : pas de tâche
     * planifiée, appelé à chaque affichage d'une page emplacements. Comble un trou de
     * {@link #syncEmplacementStatut(Contrat)} : celui-ci ne s'exécute que sur une action
     * explicite (création/modification/résiliation/suppression d'un contrat) — un contrat qui
     * expire simplement, sans qu'on y touche, ne libérait donc jamais son emplacement. Ici, on
     * vérifie directement si l'emplacement a encore un contrat VALIDER non expiré, peu importe
     * la dernière action effectuée dessus.
     */
    public void rafraichirStatut(Emplacement emplacement) {
        if (emplacement == null || emplacement.getStatut() == StatutEmplacement.EN_MAINTENANCE) {
            return;
        }
        boolean aUnContratActif = contratRepository.findByEmplacementId(emplacement.getId()).stream()
                .anyMatch(c -> c.getStatut() == StatutContrat.VALIDER
                        && (c.getDateFin() == null || !c.getDateFin().isBefore(LocalDate.now())));
        StatutEmplacement statutAttendu = aUnContratActif ? StatutEmplacement.NON_DISPONIBLE : StatutEmplacement.DISPONIBLE;
        if (emplacement.getStatut() != statutAttendu) {
            emplacement.setStatut(statutAttendu);
            emplacementRepository.save(emplacement);
        }
    }

    /** Variante en lot de {@link #rafraichirStatut(Emplacement)}, pour une liste d'emplacements. */
    public void rafraichirStatuts(List<Emplacement> emplacements) {
        emplacements.forEach(this::rafraichirStatut);
    }

    /**
     * Fait passer un contrat VALIDER dont la dateFin est dépassée à EXPIRE — recalcul
     * paresseux à l'affichage (liste des contrats, fiche détail), même principe que
     * {@link #rafraichirStatut(Emplacement)} : pas de tâche planifiée. Choix délibéré plutôt
     * qu'un batch quotidien : cette application n'a aucune infrastructure de tâche planifiée
     * (pas de @Scheduled, pas de scheduler configuré) et tous les autres statuts dérivés du
     * temps (échéances EN_RETARD, statut d'emplacement) suivent déjà ce même principe — en
     * ajouter un pour ce seul cas casserait la cohérence de l'app sans bénéfice réel : la seule
     * chose qu'un batch nocturne apporterait de plus, c'est un statut à jour même quand
     * personne ne consulte le contrat, ce qui n'a aucune valeur pratique ici (rien ne s'abonne
     * aux changements de statut contrat en dehors de l'affichage lui-même).
     *
     * Ne fait rien si le contrat n'est pas VALIDER (les autres statuts sont soit déjà terminaux
     * — RESILIER, REJETER, EXPIRE — soit EN_ATTENTE, qui n'est jamais automatiquement expiré :
     * une demande en attente qui traîne reste EN_ATTENTE, elle n'a jamais été activée).
     */
    public void verifierExpiration(Contrat contrat) {
        if (contrat == null || contrat.getStatut() != StatutContrat.VALIDER) {
            return;
        }
        if (contrat.getDateFin() != null && contrat.getDateFin().isBefore(LocalDate.now())) {
            contrat.setStatut(StatutContrat.EXPIRE);
            contratRepository.save(contrat);
        }
    }

    /** Variante en lot de {@link #verifierExpiration(Contrat)}, pour une liste de contrats. */
    public void rafraichirStatutsExpiration(List<Contrat> contrats) {
        contrats.forEach(this::verifierExpiration);
    }
}
