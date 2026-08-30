# Migration vers PostgreSQL — préconisations

Ce document capture les règles à respecter au moment de remplacer le H2 en mémoire actuel par un
vrai PostgreSQL. Rien n'est installé pour l'instant ; ceci prépare le terrain pour que ce jour-là,
seule la configuration change (via variables d'environnement, voir `.env.example`), pas le code.

## 1. Jamais d'identifiant en clair

`application.properties` lit déjà `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` depuis l'environnement
(avec des valeurs par défaut qui reproduisent le H2 actuel si elles sont absentes). Le jour de la
bascule PostgreSQL, ces trois variables doivent être définies dans `.env` (jamais committé, déjà
dans `.gitignore`) ou dans la configuration du serveur de déploiement — jamais en dur dans
`application.properties` ni dans aucun fichier suivi par Git.

## 2. Un compte applicatif dédié, pas le superutilisateur

Le compte utilisé par l'application ne doit **jamais** être `postgres` (le superutilisateur).
Créer un rôle dédié, avec des droits limités à la seule base `nezafi` :

```sql
CREATE ROLE nezafi_app WITH LOGIN PASSWORD '<mot de passe fort, hors dépôt>';
CREATE DATABASE nezafi OWNER nezafi_app;
-- Si la base existe déjà et que nezafi_app n'en est pas propriétaire :
GRANT ALL PRIVILEGES ON DATABASE nezafi TO nezafi_app;
\c nezafi
GRANT ALL PRIVILEGES ON SCHEMA public TO nezafi_app;
```

`nezafi_app` doit pouvoir créer/modifier les tables de cette base (Hibernate en a besoin), mais
n'a aucune raison d'avoir de droits sur les autres bases d'une éventuelle même instance
PostgreSQL, ni d'être superutilisateur.

## 3. Accessible uniquement depuis la machine locale

Tant que rien d'autre n'est décidé explicitement, PostgreSQL ne doit répondre que sur la machine
qui héberge l'application — pas exposé sur le réseau du secrétariat.

- `postgresql.conf` : `listen_addresses = 'localhost'` (pas `'*'`).
- `pg_hba.conf` : n'autoriser que les connexions depuis `127.0.0.1/32` et `::1/128`.
- Si l'application et PostgreSQL tournent sur des machines séparées un jour, ce point doit être
  revu explicitement (accès réseau restreint à l'IP de l'application, jamais ouvert largement) —
  pas une bascule silencieuse.

## Ce qui reste à faire le jour de la migration

- Ajouter la dépendance `org.postgresql:postgresql` au `pom.xml` (actuellement seul `h2` y est,
  en scope `runtime`).
- Définir `DB_URL=jdbc:postgresql://localhost:5432/nezafi`, `DB_USERNAME=nezafi_app`,
  `DB_PASSWORD=...` dans `.env`.
- Retirer `spring.datasource.driver-class-name=org.h2.Driver` (spécifique à H2) et
  `spring.jpa.database-platform=org.hibernate.dialect.H2Dialect` de `application.properties`
  (Hibernate détecte le dialecte PostgreSQL automatiquement depuis l'URL JDBC).
- Vérifier `spring.jpa.hibernate.ddl-auto=update` : correct pour un premier déploiement, à
  remplacer par une vraie stratégie de migration (Flyway/Liquibase) dès que la base contient des
  données réelles qu'on ne veut plus perdre à chaque changement de schéma.
