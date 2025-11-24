-- 1. Activer les extensions nécessaires
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. Créer la table standard Spring AI
CREATE TABLE IF NOT EXISTS vector_store (
                                            id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768) -- ATTENTION : 768 dimensions pour nomic-embed-text (1536 pour OpenAI)
    );

-- 3. Créer l'index HNSW pour la performance (Vital pour le RAG)
-- Cela rend la recherche super rapide même avec des millions de vecteurs
CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);