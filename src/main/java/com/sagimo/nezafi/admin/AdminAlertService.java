package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.StatutContrat;
import com.sagimo.nezafi.echeance.Echeance;
import com.sagimo.nezafi.echeance.EcheanceRepository;
import com.sagimo.nezafi.echeance.EcheanceStatusService;
import com.sagimo.nezafi.echeance.TypeEcheance;
import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.emplacement.StatutEmplacement;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Détecte les incohérences "douces" du métier : des situations qui méritent d'être
 * signalées à l'admin mais qui ne doivent jamais bloquer une saisie (contrairement
 * aux règles imposées dans {@link com.sagimo.nezafi.admin.AdminController} et
 * {@link com.sagimo.nezafi.echeance.EcheanceAdminController}) — certaines sont des cas
 * négociés hors barème parfaitement valides.
 */
@Service
public class AdminAlertService {

    // Écart toléré entre le total des échéances LOYER et le montantLoyer déclaré,
    // avant de considérer l'écart comme "significatif" : au-delà de 5% du loyer.
    private static final BigDecimal SEUIL_ECART_RELATIF = new BigDecimal("0.05");

    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;
    private final EcheanceRepository echeanceRepository;
    private final EcheanceStatusService echeanceStatusService;

    public AdminAlertService(EmplacementRepository emplacementRepository, ContratRepository contratRepository,
                              EcheanceRepository echeanceRepository, EcheanceStatusService echeanceStatusService) {
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
        this.echeanceRepository = echeanceRepository;
        this.echeanceStatusService = echeanceStatusService;
    }

    /** Emplacements NON_DISPONIBLE alors qu'aucun contrat VALIDER ne les occupe. */
    public List<Emplacement> emplacementsNonDisponiblesSansContratActif() {
        return emplacementRepository.findByStatut(StatutEmplacement.NON_DISPONIBLE).stream()
                .filter(this::sansContratActif)
                .toList();
    }

    // Vérifie aussi la dateFin (pas seulement statut==VALIDER) : un contrat VALIDER dont la
    // dateFin est dépassée mais pas encore rebasculé à EXPIRE (recalcul paresseux, cf.
    // ContratStatusService.verifierExpiration) ne compte plus comme un contrat actif ici.
    public boolean sansContratActif(Emplacement emplacement) {
        LocalDate aujourdHui = LocalDate.now();
        return contratRepository.findByEmplacementId(emplacement.getId()).stream()
                .noneMatch(c -> c.getStatut() == StatutContrat.VALIDER
                        && (c.getDateFin() == null || !c.getDateFin().isBefore(aujourdHui)));
    }

    /** Contrats dont le total des échéances de type LOYER s'écarte significativement du montantLoyer déclaré. */
    public List<Contrat> contratsAvecEcartLoyerSignificatif() {
        return contratRepository.findAll().stream()
                .filter(c -> c.getStatut() != StatutContrat.REJETER)
                .filter(this::ecartLoyerSignificatif)
                .toList();
    }

    public boolean ecartLoyerSignificatif(Contrat contrat) {
        BigDecimal montantLoyer = contrat.getMontantLoyer();
        if (montantLoyer == null) {
            return false;
        }
        // montantDu facultatif (cf. Echeance) : une échéance sans montant ne contribue simplement
        // pas à ce total, plutôt que de faire planter la somme.
        BigDecimal totalEcheancesLoyer = echeanceRepository.findByContratId(contrat.getId()).stream()
                .filter(e -> e.getType() == TypeEcheance.LOYER)
                .map(Echeance::getMontantDu)
                .filter(montant -> montant != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalEcheancesLoyer.compareTo(BigDecimal.ZERO) == 0) {
            // Aucune échéance loyer saisie : rien d'anormal en soi (échéancier pas encore fait).
            return false;
        }
        BigDecimal ecart = totalEcheancesLoyer.subtract(montantLoyer).abs();
        BigDecimal seuil = montantLoyer.abs().multiply(SEUIL_ECART_RELATIF);
        return ecart.compareTo(seuil) > 0;
    }

    /** Échéances dont la somme des paiements dépasse le montant dû. Sans montant dû renseigné
     *  (facultatif, cf. Echeance), rien à comparer : jamais considérée excédentaire ici. */
    public List<Echeance> echeancesAvecPaiementExcedentaire() {
        return echeanceRepository.findAll().stream()
                .filter(e -> e.getMontantDu() != null
                        && echeanceStatusService.totalPaye(e.getId()).compareTo(e.getMontantDu()) > 0)
                .toList();
    }
}
