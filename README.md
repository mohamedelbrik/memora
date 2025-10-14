# 🧠 Memora — Personal Memory AI Assistant

> “Your second brain — private, intelligent, and always with you.”

Memora is an open-source personal memory augmentation assistant.  
It records your thoughts, conversations, notes, and reveals them via intelligent search and context — all while keeping your privacy intact.

---

## 🚀 Vision

Humans forget.  
Memora helps you remember — across voice, text, documents — in the form of intelligent, searchable memory.

---

## 🧩 Architecture (v0.1)

```
[Device / Mobile] → [Local Agent / Backend] → [AI / NLP / Embedding] → [Vector DB] → [Frontend UI / Search]
```

Components:
- Frontend UI (React / Tauri / Electron)
- Backend agent (Spring Boot / Node)
- NLP & embedding (OpenAI embeddings or local model)
- Vector database (Weaviate / Qdrant)
- Local storage (SQLite / encrypted files)

---

## 🧰 Core Features

- Voice / text memory capture
- Semantic search (retrieve by meaning)
- Timeline view of memories
- Daily / meeting summarization
- Local-first, privacy-first design

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-------------|
| Frontend | React, TypeScript, Tauri or Electron |
| Backend | Spring Boot (Java 23) or Node.js |
| AI / NLP | OpenAI embeddings / local embedding models |
| Vector DB | Qdrant / Weaviate |
| Storage | PostgreSQL / SQLite / encrypted file store |
| Infrastructure | Docker Compose → Kubernetes (later) |

---

## 🔏 Privacy Principles

- All data processed locally
- Encrypted by default
- Optional sync with end-to-end encryption
- Full user control over data retention and deletion

---

## 🛤️ Roadmap

| Phase | Goal |
|-------|------|
| Phase 1 | MVP prototype with local memory + search |
| Phase 2 | UI / chat interface + summaries |
| Phase 3 | Encrypted sync + mobile / web app |
| Phase 4 | Integrations (Gmail, Notion, etc.) |
| Phase 5 | Cognitive reasoning layer + context awareness |

---

## 🧭 Example Usage

```
memora add "Meeting with Alice about IoT architecture"
memora search "What did Alice say about scaling Kafka?"
memora summarize week
```

---

## 👤 About the Author

**Mohamed El Brik**  
Java / Cloud / Kafka Consultant @ Zenika  
Building the bridge between human memory and AI augmentation

---

## 📜 License

MIT License — 2025  
“Your mind, your memory, your privacy.”
