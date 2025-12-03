-- 1. Ajouter une VRAIE colonne pour la date (Architecture hybride JSON/Relationnel)
ALTER TABLE vector_store
    ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMPTZ;

-- 2. Fonction Trigger : Copie la date du JSON vers la colonne à chaque écriture
CREATE OR REPLACE FUNCTION vector_store_ingested_at_trigger() RETURNS trigger AS $$
BEGIN
    -- On cast le JSON en text puis en timestamptz.
    -- C'est autorisé ici (dans un trigger) alors que c'est interdit dans un index direct.
    BEGIN
        NEW.ingested_at := (NEW.metadata->>'ingested_at')::timestamptz;
    EXCEPTION WHEN OTHERS THEN
        -- Si la date est mal formée ou absente, on met NULL (fail-safe)
        NEW.ingested_at := NULL;
    END;
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

-- 3. Attacher le trigger
DROP TRIGGER IF EXISTS ingested_at_update ON vector_store;
CREATE TRIGGER ingested_at_update
    BEFORE INSERT OR UPDATE ON vector_store
    FOR EACH ROW EXECUTE FUNCTION vector_store_ingested_at_trigger();

-- 4. Backfill : Mettre à jour les anciennes données
-- Cela va déclencher le trigger pour toutes les lignes existantes
UPDATE vector_store SET id = id;

-- 5. Créer l'index sur la colonne physique (Rapide et standard)
CREATE INDEX IF NOT EXISTS idx_vector_store_ingested_at ON vector_store (ingested_at);