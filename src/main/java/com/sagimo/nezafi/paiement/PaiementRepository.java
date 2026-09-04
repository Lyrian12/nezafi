package com.sagimo.nezafi.paiement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaiementRepository extends JpaRepository<Paiement, Long> {
    List<Paiement> findByEcheanceId(Long echeanceId);

    // Utilisé pour bloquer la suppression d'un compte du personnel qui a enregistré des
    // paiements (cf. StaffController) : la colonne admin_enregistrant_id est NOT NULL sans
    // cascade, et un paiement encaissé ne doit de toute façon jamais perdre la trace de qui
    // l'a enregistré.
    boolean existsByAdminEnregistrantId(Long adminEnregistrantId);
}
