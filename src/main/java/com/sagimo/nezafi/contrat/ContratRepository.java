package com.sagimo.nezafi.contrat;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContratRepository extends JpaRepository<Contrat, Long> {
    List<Contrat> findBoutiqueId (long boutiqueid);
    List<Contrat> findLocataireId (long locataireid);
    List<Contrat> findByStatut(StatutContrat statut);
}
