package com.sagimo.nezafi.contrat;

import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.emplacement.StatutEmplacement;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

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
 * IMPORTANT — À destination du développeur qui ajoutera le futur endpoint de création de
 * contrat (aucun {@code POST /admin/contracts/add} n'existe encore aujourd'hui, seuls
 * edit/delete sont implémentés) : {@link #syncEmplacementStatut(Contrat)} doit être appelée
 * juste après la sauvegarde de tout nouveau contrat, pour appliquer ces mêmes règles dès
 * la création.
 */
@Service
public class ContratStatusService {

    private final EmplacementRepository emplacementRepository;

    public ContratStatusService(EmplacementRepository emplacementRepository) {
        this.emplacementRepository = emplacementRepository;
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
}
