package com.sagimo.nezafi.contrat;

import com.sagimo.nezafi.boutique.Boutique;
import com.sagimo.nezafi.boutique.BoutiqueRepository;
import com.sagimo.nezafi.boutique.StatutBoutique;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Synchronise le statut d'une {@link Boutique} avec celui de son {@link Contrat}.
 *
 * Règles appliquées :
 * <ul>
 *     <li>Contrat {@code VALIDER} (et non expiré) → boutique {@code NON_DISPONIBLE}.</li>
 *     <li>Contrat {@code RESILIER}, {@code REJETER}, ou dont la {@code dateFin} est dépassée
 *         → boutique {@code DISPONIBLE}, <strong>sauf</strong> si elle est actuellement
 *         {@code EN_MAINTENANCE} : ce statut prime et n'est jamais écrasé automatiquement.</li>
 *     <li>Contrat {@code EN_ATTENTE} (et non expiré) → aucune action, le statut de la
 *         boutique n'est pas modifié.</li>
 * </ul>
 *
 * IMPORTANT — À destination du développeur qui ajoutera le futur endpoint de création de
 * contrat (aucun {@code POST /admin/contracts/add} n'existe encore aujourd'hui, seuls
 * edit/delete sont implémentés) : {@link #syncBoutiqueStatut(Contrat)} doit être appelée
 * juste après la sauvegarde de tout nouveau contrat, pour appliquer ces mêmes règles dès
 * la création.
 */
@Service
public class ContratStatusService {

    private final BoutiqueRepository boutiqueRepository;

    public ContratStatusService(BoutiqueRepository boutiqueRepository) {
        this.boutiqueRepository = boutiqueRepository;
    }

    /**
     * Applique les règles de synchronisation ci-dessus pour le contrat donné.
     * Ne fait rien si le contrat n'est rattaché à aucune boutique.
     */
    public void syncBoutiqueStatut(Contrat contrat) {
        Boutique boutique = contrat.getBoutique();
        if (boutique == null) {
            return;
        }

        boolean expire = contrat.getDateFin() != null && contrat.getDateFin().isBefore(LocalDate.now());
        boolean doitLibererLaBoutique = expire
                || contrat.getStatut() == StatutContrat.RESILIER
                || contrat.getStatut() == StatutContrat.REJETER;

        if (doitLibererLaBoutique) {
            libererBoutique(boutique);
            return;
        }

        if (contrat.getStatut() == StatutContrat.VALIDER) {
            boutique.setStatut(StatutBoutique.NON_DISPONIBLE);
            boutiqueRepository.save(boutique);
        }
        // StatutContrat.EN_ATTENTE (et non expiré) : comportement volontairement neutre,
        // le statut de la boutique n'est pas modifié tant que le contrat n'est pas validé.
    }

    /**
     * Remet une boutique à DISPONIBLE, sauf si elle est actuellement EN_MAINTENANCE
     * (ce statut prime toujours sur la libération automatique).
     *
     * Utilisée en interne par {@link #syncBoutiqueStatut(Contrat)}, et directement par la
     * suppression d'un contrat : un contrat supprimé ne pilote plus la boutique, qui doit
     * donc être libérée sans dépendre de son ancien statut.
     */
    public void libererBoutique(Boutique boutique) {
        if (boutique == null || boutique.getStatut() == StatutBoutique.EN_MAINTENANCE) {
            return;
        }
        boutique.setStatut(StatutBoutique.DISPONIBLE);
        boutiqueRepository.save(boutique);
    }
}
