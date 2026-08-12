package com.sagimo.nezafi.boutique;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Boutique {
    private long id;
    private String name;
    private String imageurl;
    private LocalDateTime addedAt;
    private StatutBoutique statut;
}
