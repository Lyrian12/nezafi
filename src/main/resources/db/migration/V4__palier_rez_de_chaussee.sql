-- Nouveau palier REZ_DE_CHAUSSEE (cf. Palier) : élargit le CHECK constraint généré par
-- Hibernate lors du schéma initial (V1), même principe que V3 pour StatutContrat.EXPIRE.
ALTER TABLE public.emplacements DROP CONSTRAINT emplacements_palier_check;
ALTER TABLE public.emplacements ADD CONSTRAINT emplacements_palier_check
    CHECK (((palier)::text = ANY ((ARRAY['REZ_DE_CHAUSSEE'::character varying, 'PALIER_1'::character varying, 'PALIER_2'::character varying, 'PALIER_3'::character varying])::text[])));
