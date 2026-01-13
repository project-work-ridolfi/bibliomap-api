/**
 * ============================================================
 * BIBLIOMAP - SCRIPT DI INIZIALIZZAZIONE DEL DATABASE
 * ============================================================
 *
 * Questo script inizializza lo schema del database MongoDB
 * utilizzato dall'applicazione Bibliomap.
 *
 * Lo script crea esplicitamente tutte le collection e applica:
 *  - identificatori (_id) basati su stringhe
 *  - vincoli di non nullabilità tramite JSON Schema validation
 *  - vincoli di unicità dove necessario
 *  - indicizzazione geospaziale per le query basate sulla posizione
 *
 * È pensato per ambienti in cui non si utilizza MongoDB Atlas
 * e si preferisce un'istanza MongoDB locale.
 *
 * Ambiente di esecuzione: mongosh
 * ============================================================
 */

/**
 * Selezione del database applicativo.
 * Se il database non esiste, verrà creato implicitamente
 * alla creazione della prima collection.
 */
const dbName = "bibliomap";
const database = db.getSiblingDB(dbName);


/**
 * ============================================================
 * COLLECTION: BOOKS
 * ============================================================
 *
 * Contiene i metadati dei libri.
 * L'identificatore _id corrisponde all'ISBN ed è memorizzato
 * come stringa.
 * Vengono resi obbligatori solo i campi identificativi principali.
 */
database.createCollection("books", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["_id", "author", "title"],
      properties: {
        _id: {
          bsonType: "string",
          description: "ISBN del libro, memorizzato come stringa"
        },
        author: {
          bsonType: "string"
        },
        title: {
          bsonType: "string"
        }
      }
    }
  },
  validationLevel: "strict"
});

/**
 * ============================================================
 * COLLECTION: COPIES
 * ============================================================
 *
 * Rappresenta le copie fisiche dei libri.
 * Ogni copia è associata a una libreria e a un libro (ISBN).
 */
database.createCollection("copies", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["_id", "libraryId", "book_isbn"],
      properties: {
        _id: {
          bsonType: "string"
        },
        libraryId: {
          bsonType: "string",
          description: "Riferimento a libraries._id"
        },
        book_isbn: {
          bsonType: "string",
          description: "Riferimento a books._id"
        }
      }
    }
  },
  validationLevel: "strict"
});

/**
 * ============================================================
 * COLLECTION: LIBRARIES
 * ============================================================
 *
 * Rappresenta una libreria appartenente a un utente.
 * A livello di schema vengono vincolati solo identificatore
 * e proprietario.
 */
database.createCollection("libraries", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["_id", "ownerId"],
      properties: {
        _id: {
          bsonType: "string"
        },
        ownerId: {
          bsonType: "string",
          description: "Riferimento a users._id"
        }
      }
    }
  },
  validationLevel: "strict"
});

/**
 * ============================================================
 * COLLECTION: LOANS
 * ============================================================
 *
 * Traccia le operazioni di prestito dei libri tra utenti.
 * Lo schema impone la presenza di tutti gli identificatori
 * fondamentali e dello stato del prestito.
 */
database.createCollection("loans", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: [
        "_id",
        "status",
        "owner_id",
        "requester_id",
        "copy_id"
      ],
      properties: {
        _id: {
          bsonType: "string"
        },
        status: {
          bsonType: "string",
          description: "Stato del prestito (es. REQUESTED, ACTIVE, RETURNED)"
        },
        owner_id: {
          bsonType: "string",
          description: "Utente proprietario della copia"
        },
        requester_id: {
          bsonType: "string",
          description: "Utente che richiede il prestito"
        },
        copy_id: {
          bsonType: "string",
          description: "Riferimento a copies._id"
        }
      }
    }
  },
  validationLevel: "strict"
});

/**
 * ============================================================
 * COLLECTION: USERS
 * ============================================================
 *
 * Contiene gli utenti dell'applicazione.
 * Email e username devono essere univoci.
 */
database.createCollection("users", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["_id", "email", "username"],
      properties: {
        _id: {
          bsonType: "string",
          description: "Identificatore UUID applicativo"
        },
        email: {
          bsonType: "string"
        },
        username: {
          bsonType: "string"
        }
      }
    }
  },
  validationLevel: "strict"
});

/**
 * Indici di unicità per garantire la consistenza
 * dell'identità degli utenti.
 */
database.users.createIndex({ email: 1 }, { unique: true });
database.users.createIndex({ username: 1 }, { unique: true });

/**
 * ============================================================
 * COLLECTION: LOCATIONS
 * ============================================================
 *
 * Contiene le informazioni geografiche associate a utenti
 * o librerie.
 *
 * Il campo geolocation segue lo standard GeoJSON:
 *  - type deve essere "Point"
 *  - coordinates è un array [longitudine, latitudine]
 *
 * La presenza di un indice 2dsphere consente:
 *  - ricerche di prossimità
 *  - query basate su raggio
 *  - utilizzo delle operazioni geospaziali di MongoDB
 */
database.createCollection("locations", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["_id", "geolocation"],
      properties: {
        _id: {
          bsonType: "string"
        },
        geolocation: {
          bsonType: "object",
          required: ["type", "coordinates"],
          properties: {
            type: {
              bsonType: "string",
              enum: ["Point"],
              description: "Tipo di geometria GeoJSON"
            },
            coordinates: {
              bsonType: "array",
              minItems: 2,
              maxItems: 2,
              items: {
                bsonType: "double"
              },
              description: "[longitudine, latitudine]"
            }
          }
        }
      }
    }
  },
  validationLevel: "strict"
});

/**
 * Indice geospaziale necessario per le query GeoJSON.
 */
database.locations.createIndex({ geolocation: "2dsphere" });

/**
 * ============================================================
 * FINE SCRIPT
 * ============================================================
 */
print("Database Bibliomap inizializzato correttamente.");
