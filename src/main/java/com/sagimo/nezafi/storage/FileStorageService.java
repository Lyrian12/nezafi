package com.sagimo.nezafi.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stockage local des fichiers uploadés (photos d'emplacements, factures de contrat, reçus de
 * paiement) : un dossier sur disque (cf. {@code app.upload-dir}), pas de service de stockage
 * externe pour cette première version.
 *
 * Chaque fichier est stocké sous {@code <sous-dossier>/<uuid>__<nom-original>} : le préfixe
 * UUID évite les collisions de noms, le nom original reste lisible et récupérable pour
 * l'affichage (cf. {@link #nomOriginal(String)}).
 */
@Service
public class FileStorageService {

    private final Path racine;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.racine = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(racine);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer le dossier de stockage " + racine, e);
        }
    }

    /**
     * Enregistre le fichier sous {@code sousDossier} et renvoie le chemin relatif à stocker en
     * base (ex. {@code "emplacements/3f2a...__photo.jpg"}).
     */
    public String enregistrer(MultipartFile fichier, String sousDossier) throws IOException {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier fourni.");
        }
        String nomOriginal = StringUtils.cleanPath(
                fichier.getOriginalFilename() == null ? "fichier" : fichier.getOriginalFilename());
        if (nomOriginal.contains("..")) {
            throw new IllegalArgumentException("Nom de fichier invalide : " + nomOriginal);
        }
        String nomStocke = UUID.randomUUID() + "__" + nomOriginal;

        Path dossier = racine.resolve(sousDossier).normalize();
        Files.createDirectories(dossier);
        Path cible = dossier.resolve(nomStocke);
        try (var in = fichier.getInputStream()) {
            Files.copy(in, cible, StandardCopyOption.REPLACE_EXISTING);
        }
        return sousDossier + "/" + nomStocke;
    }

    /** Charge le fichier désigné par son chemin relatif (tel que renvoyé par {@link #enregistrer}). */
    public Resource charger(String cheminRelatif) {
        Path fichier = racine.resolve(cheminRelatif).normalize();
        if (!fichier.startsWith(racine)) {
            // Protection basique contre une éventuelle tentative de parcours de chemin (../..).
            throw new IllegalArgumentException("Chemin de fichier invalide.");
        }
        try {
            Resource ressource = new UrlResource(fichier.toUri());
            if (!ressource.exists() || !ressource.isReadable()) {
                throw new IllegalArgumentException("Fichier introuvable : " + cheminRelatif);
            }
            return ressource;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Chemin de fichier invalide : " + cheminRelatif, e);
        }
    }

    /** Nom d'origine du fichier (celui choisi par l'utilisateur à l'upload), pour l'affichage. */
    public String nomOriginal(String cheminRelatif) {
        String nomFichier = Paths.get(cheminRelatif).getFileName().toString();
        int separateur = nomFichier.indexOf("__");
        return separateur >= 0 ? nomFichier.substring(separateur + 2) : nomFichier;
    }
}
