package com.sagimo.nezafi.config;

import com.sagimo.nezafi.contrat.Contrat;
import com.sagimo.nezafi.contrat.ContratRepository;
import com.sagimo.nezafi.contrat.StatutContrat;
import com.sagimo.nezafi.echeance.Echeance;
import com.sagimo.nezafi.echeance.EcheanceRepository;
import com.sagimo.nezafi.echeance.StatutEcheance;
import com.sagimo.nezafi.echeance.TypeEcheance;
import com.sagimo.nezafi.emplacement.CategorieEmplacement;
import com.sagimo.nezafi.emplacement.Emplacement;
import com.sagimo.nezafi.emplacement.EmplacementRepository;
import com.sagimo.nezafi.emplacement.Palier;
import com.sagimo.nezafi.emplacement.StatutEmplacement;
import com.sagimo.nezafi.paiement.Paiement;
import com.sagimo.nezafi.paiement.PaiementRepository;
import com.sagimo.nezafi.user.Role;
import com.sagimo.nezafi.user.User;
import com.sagimo.nezafi.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Jeu de données de démo/test, généré une seule fois (DB H2 en mémoire, recréée à
 * chaque démarrage). Pense à couvrir concrètement : les 3 paliers, les 3 statuts
 * d'emplacement, plusieurs statuts de contrat (y compris un résilié pour montrer
 * l'historique d'un emplacement sur plusieurs locataires successifs), et les 3
 * couleurs d'échéance (payée/en cours/en retard) sur des échéances loyer ET caution.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EmplacementRepository emplacementRepository;
    private final ContratRepository contratRepository;
    private final EcheanceRepository echeanceRepository;
    private final PaiementRepository paiementRepository;
    private final PasswordEncoder passwordEncoder;

    private User admin;

    public DataSeeder(UserRepository userRepository, EmplacementRepository emplacementRepository,
                       ContratRepository contratRepository, EcheanceRepository echeanceRepository,
                       PaiementRepository paiementRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emplacementRepository = emplacementRepository;
        this.contratRepository = contratRepository;
        this.echeanceRepository = echeanceRepository;
        this.paiementRepository = paiementRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        admin = seedAdmin();

        if (emplacementRepository.count() > 0) {
            // Déjà seedé (redémarrage sans perte de la DB en mémoire) : rien à refaire.
            return;
        }

        // --- Emplacements : répartis sur les 3 paliers, avec les 3 statuts ---
        Emplacement boutiqueTextile = creerEmplacement("Boutique Textile Mbouda", Palier.PALIER_1,
                CategorieEmplacement.BOUTIQUE, "15.00", "100000.00", StatutEmplacement.NON_DISPONIBLE);
        Emplacement boutiqueCosmetiques = creerEmplacement("Boutique Cosmétiques Bella", Palier.PALIER_1,
                CategorieEmplacement.BOUTIQUE, "12.00", "90000.00", StatutEmplacement.DISPONIBLE);
        Emplacement magasinElectronique = creerEmplacement("Magasin Électronique Fokou", Palier.PALIER_1,
                CategorieEmplacement.MAGASIN, "30.00", "200000.00", StatutEmplacement.NON_DISPONIBLE);
        Emplacement boutiqueChaussures = creerEmplacement("Boutique Chaussures Ngoue", Palier.PALIER_2,
                CategorieEmplacement.BOUTIQUE, "18.00", "120000.00", StatutEmplacement.DISPONIBLE);
        Emplacement magasinTextileImport = creerEmplacement("Magasin Textile Import", Palier.PALIER_2,
                CategorieEmplacement.MAGASIN, "40.00", "280000.00", StatutEmplacement.NON_DISPONIBLE);
        creerEmplacement("Boutique Bijoux Star", Palier.PALIER_2,
                CategorieEmplacement.BOUTIQUE, "10.00", "85000.00", StatutEmplacement.EN_MAINTENANCE);
        creerEmplacement("Magasin Alimentation Générale", Palier.PALIER_3,
                CategorieEmplacement.MAGASIN, "50.00", "350000.00", StatutEmplacement.DISPONIBLE);
        creerEmplacement("Boutique Prêt-à-porter Élégance", Palier.PALIER_3,
                CategorieEmplacement.BOUTIQUE, "20.00", "140000.00", StatutEmplacement.DISPONIBLE);

        // --- Clients ---
        User paulKamdem = creerClient("Kamdem", "Paul", "+237690000001", "paul.kamdem@example.com");
        User aminatouNjoya = creerClient("Njoya", "Aminatou", "+237690000002", "aminatou.njoya@example.com");
        User jeanBaptisteFotso = creerClient("Fotso", "Jean-Baptiste", "+237690000003", null);
        User marieAteba = creerClient("Ateba", "Marie", "+237690000004", "marie.ateba@example.com");
        User samuelMballa = creerClient("Mballa", "Samuel", "+237690000005", "samuel.mballa@example.com");
        creerClient("Talla", "Grace", "+237690000006", "grace.talla@example.com"); // aucun contrat, pour tester l'état vide

        // --- Contrat A : historique — ancien locataire de la boutique textile, résilié ---
        Contrat contratHistorique = creerContrat(boutiqueTextile, samuelMballa, StatutContrat.RESILIER,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                "1000000.00", 12, "1000000.00", 12);
        contratHistorique.setMotifResiliation("Fin de bail, départ du locataire à l'échéance normale du contrat.");
        contratRepository.save(contratHistorique);
        payer(creerEcheance(contratHistorique, TypeEcheance.LOYER, LocalDate.of(2025, 1, 10), "1000000.00"),
                LocalDate.of(2025, 1, 10));

        // --- Contrat B : locataire actuel de la même boutique — montre l'historique sur 2 locataires ---
        Contrat contratKamdem = creerContrat(boutiqueTextile, paulKamdem, StatutContrat.VALIDER,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "1200000.00", 12, "1200000.00", 12);
        payer(creerEcheance(contratKamdem, TypeEcheance.LOYER, LocalDate.of(2026, 1, 5), "400000.00"),
                LocalDate.of(2026, 1, 5));
        creerEcheance(contratKamdem, TypeEcheance.LOYER, LocalDate.of(2026, 5, 5), "400000.00"); // volontairement en retard
        creerEcheance(contratKamdem, TypeEcheance.LOYER, LocalDate.of(2026, 10, 5), "400000.00"); // pas encore due
        payer(creerEcheance(contratKamdem, TypeEcheance.CAUTION, LocalDate.of(2026, 1, 5), "1200000.00"),
                LocalDate.of(2026, 1, 5)); // caution payée cash en une fois

        // --- Contrat C : caution étalée sur moins d'un an (6 mois), payée cash aussi ---
        Contrat contratNjoya = creerContrat(magasinElectronique, aminatouNjoya, StatutContrat.VALIDER,
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28),
                "2400000.00", 12, "1200000.00", 6);
        payer(creerEcheance(contratNjoya, TypeEcheance.LOYER, LocalDate.of(2026, 3, 5), "1200000.00"),
                LocalDate.of(2026, 3, 5));
        creerEcheance(contratNjoya, TypeEcheance.LOYER, LocalDate.of(2026, 9, 5), "1200000.00"); // pas encore due
        payer(creerEcheance(contratNjoya, TypeEcheance.CAUTION, LocalDate.of(2026, 3, 10), "1200000.00"),
                LocalDate.of(2026, 3, 10));

        // --- Contrat D : plusieurs échéances loyer en retard + une caution étalée pas encore due ---
        Contrat contratFotso = creerContrat(magasinTextileImport, jeanBaptisteFotso, StatutContrat.VALIDER,
                LocalDate.of(2026, 2, 1), LocalDate.of(2027, 1, 31),
                "3360000.00", 12, "2800000.00", 10);
        payer(creerEcheance(contratFotso, TypeEcheance.LOYER, LocalDate.of(2026, 2, 5), "840000.00"),
                LocalDate.of(2026, 2, 5));
        creerEcheance(contratFotso, TypeEcheance.LOYER, LocalDate.of(2026, 5, 5), "840000.00"); // en retard
        creerEcheance(contratFotso, TypeEcheance.LOYER, LocalDate.of(2026, 8, 5), "840000.00"); // en retard
        creerEcheance(contratFotso, TypeEcheance.LOYER, LocalDate.of(2026, 11, 5), "840000.00"); // pas encore due
        creerEcheance(contratFotso, TypeEcheance.CAUTION, LocalDate.of(2026, 12, 15), "2800000.00"); // pas encore due

        // --- Contrat E : en attente de validation, l'emplacement reste donc disponible ---
        Contrat contratAteba = creerContrat(boutiqueChaussures, marieAteba, StatutContrat.EN_ATTENTE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31),
                "1440000.00", 12, "1440000.00", 12);
        creerEcheance(contratAteba, TypeEcheance.LOYER, LocalDate.of(2026, 9, 5), "1440000.00"); // pas encore due
    }

    private User seedAdmin() {
        return userRepository.findByEmail("admin@nezafi.com").orElseGet(() -> {
            User a = new User();
            a.setNom("Nezafi");
            a.setPrenom("Admin");
            a.setTelephone("+212600000000");
            a.setEmail("admin@nezafi.com");
            a.setPassword(passwordEncoder.encode("admin123"));
            a.setRole(Role.ROLE_ADMIN);
            return userRepository.save(a);
        });
    }

    private Emplacement creerEmplacement(String name, Palier palier, CategorieEmplacement categorie,
                                          String superficie, String prix, StatutEmplacement statut) {
        Emplacement emplacement = new Emplacement();
        emplacement.setName(name);
        emplacement.setImageUrl("https://images.unsplash.com/photo-1521572267360-ee0c2909d518");
        emplacement.setStatut(statut);
        emplacement.setPalier(palier);
        emplacement.setSuperficie(new BigDecimal(superficie));
        emplacement.setPrix(new BigDecimal(prix));
        emplacement.setCategorie(categorie);
        return emplacementRepository.save(emplacement);
    }

    private User creerClient(String nom, String prenom, String telephone, String email) {
        User client = new User();
        client.setNom(nom);
        client.setPrenom(prenom);
        client.setTelephone(telephone);
        client.setEmail(email);
        client.setRole(Role.ROLE_LOCATAIRE);
        // Comptes de démo créés côté admin : mot de passe aléatoire inutilisable, même
        // logique que la création d'un client depuis l'UI (cf. AdminController.addClient).
        client.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        return userRepository.save(client);
    }

    private Contrat creerContrat(Emplacement emplacement, User locataire, StatutContrat statut,
                                  LocalDate dateDebut, LocalDate dateFin,
                                  String montantLoyer, int dureeLoyerMois,
                                  String montantCaution, int dureeCautionMois) {
        Contrat contrat = new Contrat();
        contrat.setEmplacement(emplacement);
        contrat.setLocataire(locataire);
        contrat.setDateDebut(dateDebut);
        contrat.setDateFin(dateFin);
        contrat.setStatut(statut);
        contrat.setMontantLoyer(new BigDecimal(montantLoyer));
        contrat.setDureeLoyerMois(dureeLoyerMois);
        contrat.setMontantCaution(new BigDecimal(montantCaution));
        contrat.setDureeCautionMois(dureeCautionMois);
        return contratRepository.save(contrat);
    }

    /** Échéance créée EN_COURS par défaut (pas encore due, ou volontairement laissée impayée
     *  malgré une date passée pour simuler un retard) — {@link #payer} la fait passer PAYEE. */
    private Echeance creerEcheance(Contrat contrat, TypeEcheance type, LocalDate dateEcheance, String montant) {
        Echeance echeance = new Echeance();
        echeance.setContrat(contrat);
        echeance.setType(type);
        echeance.setDateEcheance(dateEcheance);
        echeance.setMontantDu(new BigDecimal(montant));
        // EN_RETARD si la date est déjà passée à la génération (simulateur de retard),
        // sinon EN_COURS : reproduit exactement la règle d'EcheanceStatusService.
        echeance.setStatut(dateEcheance.isBefore(LocalDate.now()) ? StatutEcheance.EN_RETARD : StatutEcheance.EN_COURS);
        return echeanceRepository.save(echeance);
    }

    private void payer(Echeance echeance, LocalDate datePaiement) {
        Paiement paiement = new Paiement();
        paiement.setEcheance(echeance);
        paiement.setMontantPaye(echeance.getMontantDu());
        paiement.setDatePaiement(datePaiement);
        paiement.setAdminEnregistrant(admin);
        paiementRepository.save(paiement);

        echeance.setStatut(StatutEcheance.PAYEE);
        echeanceRepository.save(echeance);
    }
}
