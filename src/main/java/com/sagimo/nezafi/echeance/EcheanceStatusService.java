package com.sagimo.nezafi.echeance;

import com.sagimo.nezafi.paiement.Paiement;
import com.sagimo.nezafi.paiement.PaiementRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Recalcule le statut d'une {@link Echeance} à partir de la somme de ses paiements
 * et de la date du jour. Pas de tâche planifiée : le recalcul se fait "à la volée"
 * — appelé juste après l'enregistrement d'un paiement, et paresseusement à chaque
 * affichage d'échéances (fiche contrat, liste des retards) pour faire remonter les
 * échéances devenues EN_RETARD sans qu'aucun paiement n'ait été touché.
 *
 * Règles :
 * <ul>
 *     <li>Somme des paiements ≥ montant dû → PAYEE (vert) : soldée.</li>
 *     <li>Sinon, si la date d'échéance est dépassée → EN_RETARD (rouge).</li>
 *     <li>Sinon → EN_COURS (orange) : une promesse de paiement à venir, pas un
 *         problème — une échéance n'est jamais EN_RETARD avant sa propre date.</li>
 * </ul>
 * Un paiement partiel avant l'échéance reste donc EN_COURS (pas de statut dédié
 * "partiellement payée" demandé) — le montant réellement payé doit être affiché à
 * côté du statut dans les vues pour rester visible.
 */
@Service
public class EcheanceStatusService {

    private final EcheanceRepository echeanceRepository;
    private final PaiementRepository paiementRepository;

    public EcheanceStatusService(EcheanceRepository echeanceRepository, PaiementRepository paiementRepository) {
        this.echeanceRepository = echeanceRepository;
        this.paiementRepository = paiementRepository;
    }

    public BigDecimal totalPaye(Long echeanceId) {
        return paiementRepository.findByEcheanceId(echeanceId).stream()
                .map(Paiement::getMontantPaye)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void recalculerStatut(Echeance echeance) {
        BigDecimal totalPaye = totalPaye(echeance.getId());

        if (totalPaye.compareTo(echeance.getMontantDu()) >= 0) {
            echeance.setStatut(StatutEcheance.PAYEE);
        } else if (echeance.getDateEcheance().isBefore(LocalDate.now())) {
            echeance.setStatut(StatutEcheance.EN_RETARD);
        } else {
            echeance.setStatut(StatutEcheance.EN_COURS);
        }
        echeanceRepository.save(echeance);
    }

    public void rafraichirStatuts(List<Echeance> echeances) {
        echeances.forEach(this::recalculerStatut);
    }
}
