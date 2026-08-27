package com.sagimo.nezafi.boutique;

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
@Table(name = "boutiques")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Boutique {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String imageUrl;

    @Column(nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutBoutique statut = StatutBoutique.DISPONIBLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Palier palier;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal superficie;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategorieBoutique categorie;

    @JsonIgnore
    @OneToMany(mappedBy = "boutique", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contrat> contrats = new ArrayList<>();
}
