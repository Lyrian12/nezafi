package com.sagimo.nezafi.admin;

import com.sagimo.nezafi.user.User;

/** Une ligne de la tuile "Gestion des clients" du tableau de bord. */
public record ClientApercu(User client, int nombreContrats, boolean contratActif) {
}
