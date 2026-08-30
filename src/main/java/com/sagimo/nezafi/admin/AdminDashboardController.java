package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.audit.JournalAudit;
import com.sagimo.nezafi.audit.JournalAuditRepository;
import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.ContratStatusService;
import com.sagimo.nezafi.contrat.StatutContrat;
import com.sagimo.nezafi.echeance.Echeance;
import com.sagimo.nezafi.echeance.EcheanceRepository;
import com.sagimo.nezafi.echeance.EcheanceStatusService;
import com.sagimo.nezafi.echeance.StatutEcheance;
import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.emplacement.Palier;
import com.sagimo.nezafi.emplacement.StatutEmplacement;
import com.sagimo.nezafi.paiement.Paiement;
import com.sagimo.nezafi.paiement.PaiementRepository;
import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tableau de bord admin : point d'entrée du panneau, agrège en lecture seule un aperçu de
 * chacun des modules (emplacements, contrats, clients, échéances, audit). Volontairement
 * séparé d'{@link AdminController} (déjà volumineux) — même logique de séparation par
 * concern que {@link com.sagimo.nezafi.echeance.EcheanceAdminController} et
 * {@link com.sagimo.nezafi.audit.AuditAdminController}, tous deux déjà des contrôleurs
 * indépendants sous {@code /admin}.
 */
@Controller
@RequestMapping("/admin")
// SECRETARIAT et COMPTABLE y accèdent aussi (occupation, alertes, aperçus utiles au quotidien)
// mais sans le bloc "Journal d'audit" — masqué dans la vue, cf. le paramètre estAdmin
// ci-dessous, ni la tuile "Gestion du personnel", ni la liste des comptes du personnel.
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIAT','COMPTABLE')")
public class AdminDashboardController {

    // Fenêtre du graphique d'encaissements : les 6 derniers mois glissants, mois courant inclus.
    private static final int MOIS_HISTORIQUE = 6;
    // Une couleur par Palier, réutilisée à la fois pour ses anneaux d'occupation et sa légende.
    private static final String[] COULEURS_PALIER = {"#84cc16", "#0ea5e9", "#f59e0b"};
    // Rayon de chaque anneau (du plus externe au plus interne, un par Palier dans l'ordre de
    // Palier.values()) — cercle SVG centré sur (80,80), épaisseur de trait 12 (cf. gabarit
    // "occupancyRingStrokeWidth" côté template), 6px d'écart entre anneaux successifs.
    private static final double[] RAYONS_ANNEAUX = {68, 50, 32};

    private final AdminAlertService adminAlertService;
    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;
    private final ContratStatusService contratStatusService;
    private final UserRepository userRepository;
    private final EcheanceRepository echeanceRepository;
    private final EcheanceStatusService echeanceStatusService;
    private final PaiementRepository paiementRepository;
    private final JournalAuditRepository journalAuditRepository;

    public AdminDashboardController(AdminAlertService adminAlertService, EmplacementRepository emplacementRepository,
                                     ContratRepository contratRepository, ContratStatusService contratStatusService,
                                     UserRepository userRepository,
                                     EcheanceRepository echeanceRepository, EcheanceStatusService echeanceStatusService,
                                     PaiementRepository paiementRepository, JournalAuditRepository journalAuditRepository) {
        this.adminAlertService = adminAlertService;
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
        this.contratStatusService = contratStatusService;
        this.userRepository = userRepository;
        this.echeanceRepository = echeanceRepository;
        this.echeanceStatusService = echeanceStatusService;
        this.paiementRepository = paiementRepository;
        this.journalAuditRepository = journalAuditRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) Long emplacementId,
                             @RequestParam(required = false) StatutEcheance statutEcheance,
                             Model model, Authentication authentication) {
        boolean estAdmin = authentication.getAuthorities().stream()
                .anyMatch(autorite -> autorite.getAuthority().equals("ROLE_ADMIN"));
        model.addAttribute("estAdmin", estAdmin);

        // Message de bienvenue personnalisé, en haut du tableau de bord (ADMIN/SECRETARIAT/
        // COMPTABLE, les 3 rôles qui atterrissent ici).
        String identifiant = authentication.getName();
        userRepository.findByEmail(identifiant).or(() -> userRepository.findByTelephone(identifiant))
                .ifPresent(utilisateur -> model.addAttribute("nomUtilisateurConnecte",
                        utilisateur.getPrenom() + " " + utilisateur.getNom()));

        // Rattrape en premier les emplacements dont le contrat a simplement expiré sans qu'on y
        // touche (cf. ContratStatusService.rafraichirStatut), avant tout calcul d'alerte ou
        // d'occupation ci-dessous — sinon l'alerte "orphelin" se déclencherait sur un état déjà
        // corrigé une ligne plus bas, incohérence purement liée à l'ordre des appels.
        List<Emplacement> allStores = emplacementRepository.findAll();
        contratStatusService.rafraichirStatuts(allStores);

        // Alertes de cohérence (mêmes règles que l'ancien widget de /admin/stores)
        model.addAttribute("emplacementsOrphelins", adminAlertService.emplacementsNonDisponiblesSansContratActif());
        model.addAttribute("contratsEcartLoyer", adminAlertService.contratsAvecEcartLoyerSignificatif());
        model.addAttribute("echeancesExcedentaires", adminAlertService.echeancesAvecPaiementExcedentaire());

        // Occupation par palier (donut) — même calcul que l'ancien widget de /admin/stores.
        List<PalierOccupancy> occupancyByPalier = Arrays.stream(Palier.values())
                .map(p -> {
                    long total = allStores.stream().filter(b -> b.getPalier() == p).count();
                    long occupied = allStores.stream()
                            .filter(b -> b.getPalier() == p && b.getStatut() == StatutEmplacement.NON_DISPONIBLE)
                            .count();
                    double percentage = total == 0 ? 0.0 : (occupied * 100.0 / total);
                    return new PalierOccupancy(p, occupied, total, percentage);
                })
                .toList();
        model.addAttribute("occupancyByPalier", occupancyByPalier);
        model.addAttribute("couleursPalier", COULEURS_PALIER);
        model.addAttribute("occupancyRings", buildOccupancyRings(occupancyByPalier));
        long totalOccupied = occupancyByPalier.stream().mapToLong(PalierOccupancy::occupiedCount).sum();
        long totalStores = occupancyByPalier.stream().mapToLong(PalierOccupancy::totalCount).sum();
        model.addAttribute("tauxOccupationGlobal", totalStores == 0 ? 0.0 : (totalOccupied * 100.0 / totalStores));

        // Gestion des contrats : contrats validés arrivant à expiration sous 30 jours — même
        // requête que AdminController.contractsPage(), plus actionnable qu'une liste arbitraire.
        LocalDate today = LocalDate.now();
        List<Contrat> contratsAExpirer = contratRepository
                .findByStatutAndDateFinBetweenOrderByDateFinAsc(StatutContrat.VALIDER, today, today.plusDays(30));
        model.addAttribute("contratsAExpirer", contratsAExpirer.stream().limit(6).toList());
        model.addAttribute("nombreContratsAExpirer", contratsAExpirer.size());

        // Gestion des clients : derniers clients inscrits (id décroissant, faute de date de
        // création sur User) avec leur nombre de contrats et s'ils ont un contrat actif.
        List<User> clients = userRepository.findByRole(Role.ROLE_LOCATAIRE).stream()
                .sorted(Comparator.comparing(User::getId).reversed())
                .limit(6)
                .toList();
        List<ClientApercu> apercuClients = clients.stream()
                .map(c -> {
                    List<Contrat> contratsClient = contratRepository.findByLocataireId(c.getId());
                    // Vérifie aussi la dateFin (pas seulement statut==VALIDER), même raison que
                    // partout ailleurs : un contrat VALIDER simplement périmé ne compte plus
                    // comme actif.
                    boolean actif = contratsClient.stream().anyMatch(ct -> ct.getStatut() == StatutContrat.VALIDER
                            && (ct.getDateFin() == null || !ct.getDateFin().isBefore(today)));
                    return new ClientApercu(c, contratsClient.size(), actif);
                })
                .toList();
        model.addAttribute("apercuClients", apercuClients);
        model.addAttribute("totalClients", userRepository.findByRole(Role.ROLE_LOCATAIRE).size());

        // Échéances : TOUTES par défaut (retard d'abord, puis en cours, puis payées — chacune
        // triée par date), filtrables sur place via ?statutEcheance=... sans passer par la page
        // séparée /admin/echeances (le lien "Voir tout" reste disponible pour la vue complète
        // avec ses propres filtres, mais n'est plus le seul moyen de filtrer).
        List<Echeance> toutesEcheances = echeanceRepository.findAll();
        echeanceStatusService.rafraichirStatuts(toutesEcheances);
        List<Echeance> echeancesApercu = toutesEcheances.stream()
                .filter(e -> statutEcheance == null || e.getStatut() == statutEcheance)
                .sorted(Comparator.comparing((Echeance e) -> prioriteStatutEcheance(e.getStatut()))
                        .thenComparing(Echeance::getDateEcheance))
                .limit(8)
                .toList();
        Map<Long, BigDecimal> totalPayeParEcheance = new HashMap<>();
        for (Echeance echeance : echeancesApercu) {
            totalPayeParEcheance.put(echeance.getId(), echeanceStatusService.totalPaye(echeance.getId()));
        }
        model.addAttribute("echeancesApercu", echeancesApercu);
        model.addAttribute("totalPayeParEcheance", totalPayeParEcheance);
        model.addAttribute("statutEcheanceFiltre", statutEcheance);
        model.addAttribute("statutsEcheance", StatutEcheance.values());

        // Journal d'audit : dernières entrées — réservé à ADMIN (estAdmin), SECRETARIAT n'a
        // pas accès au journal d'audit ; le bloc correspondant est masqué côté template. Même
        // restriction pour la liste des comptes du personnel juste en dessous (gestion des
        // comptes déjà réservée à ADMIN sur /admin/staff, cohérent de la masquer ici aussi).
        if (estAdmin) {
            List<JournalAudit> dernieresEntreesAudit = journalAuditRepository.findAllByOrderByDateActionDesc().stream()
                    .limit(6)
                    .toList();
            model.addAttribute("dernieresEntreesAudit", dernieresEntreesAudit);

            List<User> personnel = userRepository
                    .findByRoleIn(List.of(Role.ROLE_ADMIN, Role.ROLE_SECRETARIAT, Role.ROLE_COMPTABLE)).stream()
                    .sorted(Comparator.comparing(User::getNom))
                    .toList();
            model.addAttribute("personnel", personnel);
        }

        // Encaissements réellement payés par mois (pas le loyer affiché), filtrables par
        // emplacement — remplace l'ancien "revenueToday" (qui ne mesurait qu'un loyer théorique).
        List<Emplacement> emplacementsTries = allStores.stream()
                .sorted(Comparator.comparing(Emplacement::getName))
                .toList();
        model.addAttribute("emplacements", emplacementsTries);
        model.addAttribute("emplacementFiltre", emplacementId);

        List<RevenuePoint> tendanceEncaissements = calculerTendanceEncaissements(emplacementId);
        model.addAttribute("tendanceEncaissements", tendanceEncaissements);
        model.addAttribute("courbePoints", tendanceEncaissements.stream()
                .map(p -> formatDecimal(p.x()) + "," + formatDecimal(p.y()))
                .reduce((a, b) -> a + " " + b)
                .orElse(""));
        model.addAttribute("aireChemin", buildAreaPath(tendanceEncaissements));
        BigDecimal totalMoisCourant = tendanceEncaissements.isEmpty()
                ? BigDecimal.ZERO
                : tendanceEncaissements.get(tendanceEncaissements.size() - 1).total();
        model.addAttribute("encaissementMoisCourant", totalMoisCourant);

        return "admin-dashboard";
    }

    /**
     * Un anneau de progression par palier, chacun affichant son propre taux d'occupation
     * (occupé/total de CE palier) — pas sa part du total occupé tous paliers confondus.
     * Ces pourcentages sont indépendants les uns des autres (un palier à 3 emplacements
     * peut être à 100% pendant qu'un autre à 50 emplacements est à 20%), donc un seul
     * cercle "parts d'un tout" les représenterait mal : chaque palier a son propre anneau
     * concentrique, comme des anneaux d'activité.
     */
    private List<OccupancyRing> buildOccupancyRings(List<PalierOccupancy> occupancy) {
        List<OccupancyRing> anneaux = new ArrayList<>();
        for (int i = 0; i < occupancy.size(); i++) {
            double rayon = RAYONS_ANNEAUX[i % RAYONS_ANNEAUX.length];
            double circonference = 2 * Math.PI * rayon;
            double arc = circonference * (occupancy.get(i).percentage() / 100.0);
            String dashArray = formatDecimal(arc) + " " + formatDecimal(circonference);
            anneaux.add(new OccupancyRing(rayon, dashArray, COULEURS_PALIER[i % COULEURS_PALIER.length]));
        }
        return anneaux;
    }

    /**
     * Somme des paiements par mois sur les {@link #MOIS_HISTORIQUE} derniers mois, filtrée
     * sur un emplacement si fourni, avec coordonnées x/y déjà normalisées pour un polyline SVG
     * (repère 0..100 en x, 0..40 en y, base à y=40) — pas d'agrégation JPQL : suit la convention
     * du reste du code (occupation, alertes, totalPaye), tout en flux Java.
     */
    private List<RevenuePoint> calculerTendanceEncaissements(Long emplacementId) {
        YearMonth moisCourant = YearMonth.now();
        YearMonth premierMois = moisCourant.minusMonths(MOIS_HISTORIQUE - 1);

        List<Paiement> paiements = paiementRepository.findAll().stream()
                .filter(p -> emplacementId == null
                        || emplacementId.equals(p.getEcheance().getContrat().getEmplacement().getId()))
                .toList();

        Map<YearMonth, BigDecimal> totalParMois = new LinkedHashMap<>();
        for (int i = 0; i < MOIS_HISTORIQUE; i++) {
            totalParMois.put(premierMois.plusMonths(i), BigDecimal.ZERO);
        }
        for (Paiement paiement : paiements) {
            YearMonth mois = YearMonth.from(paiement.getDatePaiement());
            if (totalParMois.containsKey(mois)) {
                totalParMois.merge(mois, paiement.getMontantPaye(), BigDecimal::add);
            }
        }

        BigDecimal max = totalParMois.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<YearMonth> mois = new ArrayList<>(totalParMois.keySet());
        List<RevenuePoint> points = new ArrayList<>();
        for (int i = 0; i < mois.size(); i++) {
            YearMonth m = mois.get(i);
            BigDecimal total = totalParMois.get(m);
            double x = mois.size() == 1 ? 50 : (i * 100.0 / (mois.size() - 1));
            double normalise = max.compareTo(BigDecimal.ZERO) == 0 ? 0.0 : total.doubleValue() / max.doubleValue();
            double y = 38 - (normalise * 34);
            String label = m.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.FRENCH);
            points.add(new RevenuePoint(m, capitalize(label), total, x, y));
        }
        return points;
    }

    /** Ordre d'affichage du widget "Échéances" : en retard d'abord (le plus actionnable), puis
     *  en cours, puis payées — pour que le filtre par défaut (aucun) reste utile malgré
     *  l'inclusion des échéances payées. */
    private int prioriteStatutEcheance(StatutEcheance statut) {
        return switch (statut) {
            case EN_RETARD -> 0;
            case EN_COURS -> 1;
            case PAYEE -> 2;
        };
    }

    private String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String buildAreaPath(List<RevenuePoint> points) {
        if (points.isEmpty()) {
            return "";
        }
        StringBuilder path = new StringBuilder();
        path.append("M ").append(formatDecimal(points.get(0).x())).append(",40 ");
        for (RevenuePoint point : points) {
            path.append("L ").append(formatDecimal(point.x())).append(",").append(formatDecimal(point.y())).append(" ");
        }
        path.append("L ").append(formatDecimal(points.get(points.size() - 1).x())).append(",40 Z");
        return path.toString();
    }

    // Locale.ROOT forcé : le séparateur décimal doit rester un point (CSS/SVG), indépendamment
    // de la locale par défaut de la JVM (une locale FR produirait une virgule invalide ici).
    private String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
