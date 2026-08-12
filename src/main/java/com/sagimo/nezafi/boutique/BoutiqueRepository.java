package com.sagimo.nezafi.boutique;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BoutiqueRepository extends JpaRepository<Boutique, Long> {

    List<Boutique> findByStatut(StatutBoutique statut);
}
