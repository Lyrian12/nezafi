package com.sagimo.nezafi.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sagimo.nezafi.contrat.Contrat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true)
    private String telephone;

    @Column(nullable = true, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = true)
    private String numeroCNI;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @JsonIgnore
    @OneToMany(mappedBy = "locataire", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contrat> contrats = new ArrayList<>();
}
