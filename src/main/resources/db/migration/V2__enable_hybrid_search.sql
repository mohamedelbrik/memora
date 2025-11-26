-- 1. Ajouter la colonne pour la recherche lexicale (Full Text Search)
ALTER TABLE vector_store
    ADD COLUMN IF NOT EXISTS content_search tsvector;

-- 2. Créer un index GIN (Generalized Inverted Index)
-- C'est ce qui permet à Postgres de trouver un mot dans 1 million de lignes en 2ms.
CREATE INDEX IF NOT EXISTS idx_vector_store_content_search
    ON vector_store USING GIN(content_search);

-- 3. Créer une fonction de mise à jour automatique
-- On concatène le contenu et les métadonnées pour tout indexer

CREATE OR REPLACE FUNCTION vector_store_tsvector_trigger() RETURNS trigger AS $$
BEGIN
  -- MODIFICATION ICI : 'simple' au lieu de 'french'
  NEW.content_search :=
  setweight(to_tsvector('simple', coalesce(NEW.content, '')), 'A') ||
  setweight(to_tsvector('simple', coalesce(NEW.metadata->>'source', '')), 'B');
RETURN NEW;
END
$$ LANGUAGE plpgsql;


-- 4. Attacher le trigger à la table
-- À chaque INSERT ou UPDATE, la colonne content_search sera recalculée
DROP TRIGGER IF EXISTS tsvectorupdate ON vector_store;
CREATE TRIGGER tsvectorupdate
    BEFORE INSERT OR UPDATE ON vector_store
                         FOR EACH ROW EXECUTE FUNCTION vector_store_tsvector_trigger();

-- 5. Rétro-ingénierie : Mettre à jour les données existantes
-- Cette astuce force le trigger à s'exécuter sur toutes les lignes déjà présentes
UPDATE vector_store SET id = id;
