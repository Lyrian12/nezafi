package com.sagimo.nezafi.emplacement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sagimo.nezafi.contrat.Contrat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "emplacements")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Emplacement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Les photos vivent désormais dans com.sagimo.nezafi.storage.DocumentJoint
    // (nomEntite="Emplacement", entiteId=this.id) — plusieurs photos possibles, cf.
    // DocumentJointService.lister(...) / .premier(...) pour la vignette de couverture.

    @Column(nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutEmplacement statut = StatutEmplacement.DISPONIBLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Palier palier;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal superficie;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategorieEmplacement categorie;

    @JsonIgnore
    @OneToMany(mappedBy = "emplacement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contrat> contrats = new ArrayList<>();
}
