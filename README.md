# bibliomap-api

Questo progetto contiene il backend dell'applicazione Bibliomap. Il codice è sviluppato in Java e utilizza il framework Quarkus.

## INDEX

- [Documentazione API](#documentazione-api)
- [Diagrammi e Documentazione visuale](#diagrammi-e-documentazione-visuale)
- [Installazione](#installazione)
- [Prerequisiti](#prerequisiti)
- [In locale](#in-locale)
- [Docker](#docker)
- [Troubleshooting](#troubleshooting)
- [Quarkus](#sviluppo-con-quarkus)
- [Dipendenze](#dipendenze-e-tecnologie)
- [MongoDB](#mongo-db)
- [Changelog](./docs/changelog.md)
- [TODO](#todo)

## Documentazione API

La specifica tecnica delle API è disponibile visualizzando il file [openapi.yaml](./docs/openapi.yaml).

Le API sono organizzate in diverse risorse logiche:

- **Auth Resource**: gestione autenticazione (registrazione, login, reset password, verifica OTP)
- **User Resource**: profilo utente, impostazioni privacy, esportazione dati GDPR
- **Library Resource**: CRUD delle librerie personali con gestione location e visibilità
- **Book/Copy Resource**: catalogazione libri, ricerca geografica, gestione copie fisiche
- **Loan Resource**: ciclo completo dei prestiti (richiesta, approvazione, consegna, restituzione, estensione)
- **Stats Resource**: statistiche globali e per utente sull'utilizzo della piattaforma

Gli endpoint principali includono la ricerca di libri nelle vicinanze (`/api/books/nearby`), la gestione dello stato dei prestiti (`/api/loan/{id}/status`), e l'integrazione con Google Books API per il recupero automatico dei metadati (`/api/books/external/lookup-metadata`).

## Diagrammi e Documentazione Visuale

Per una comprensione più approfondita dell'architettura e dei flussi applicativi, è disponibile una raccolta di diagrammi in formato draw.io al percorso [docs/bibliomap.drawio](./docs/bibliomap.drawio).

La documentazione include:

- **ERD** dello schema database MongoDB
- **Diagramma di deployment** dell'infrastruttura
- **Diagramma di sequenza** per l'integrazione con Google Books API
- **Flussi applicativi** di registrazione utente e gestione prestiti
- **Macchina a stati** del ciclo di vita dei prestiti

Questi diagrammi facilitano la comprensione delle relazioni tra le entità, dei flussi operativi e delle interazioni tra i componenti del sistema.

## Installazione

Questa sezione descrive i passaggi necessari per configurare, installare e avviare l'applicazione sia in ambiente locale che tramite Docker.

### Prerequisiti

Prima di procedere, assicurarsi di avere installato:

- Java: Versione 21 o superiore
- Maven: Versione 3.9.0 o superiore

L'applicazione richiede la configurazione dei seguenti servizi esterni:

#### MongoDB Atlas (o istanza locale)

Database principale dell'applicazione. È possibile:

- Utilizzare MongoDB Atlas (cloud): creare un cluster gratuito su https://www.mongodb.com/cloud/atlas
- Utilizzare un'istanza locale: installare MongoDB Community Edition

Per l'inizializzazione del database con schema e dati di test, consultare la sezione [MongoDB](#mongo-db), nello specifico[Inizializzazione e Setup Locale](#inizializzazione-e-setup-locale).

#### Redis (o Upstash Redis)

Utilizzato per la gestione delle sessioni utente. È possibile:

- Utilizzare Upstash Redis (cloud): creare un database gratuito su https://upstash.com/
- Utilizzare un'istanza locale: installare Redis

#### Google Books API

Servizio per il recupero automatico dei metadati dei libri.

- Ottenere una API Key gratuita dalla Google Cloud Console: https://console.cloud.google.com/
- Abilitare l'API "Books API" nel progetto

#### Brevo

Servizio per l'invio di email transazionali (OTP, notifiche prestiti).

- Creare un account gratuito su https://www.brevo.com/
- Generare una API Key dalla sezione "SMTP & API"

### Configurazione Ambiente

Il progetto utilizza variabili d'ambiente per la configurazione dei servizi esterni. Queste variabili devono essere impostate prima dell'avvio dell'applicazione.

#### Variabili Richieste

Consiglio di creare un file di script (es. `set-env.sh` su Linux/Mac o `set-env.bat` su Windows) nella radice del progetto con le seguenti variabili, è possibile contattarmi a adriana.ridolfi@studenti.unipegaso.it per averne uno già compilato:

```bash
# Stringa di connessione MongoDB
export MONGODB_CONNECTION_STRING="mongodb+srv://username:password@cluster.mongodb.net/"

# Stringa di connessione Redis
export REDIS_HOSTS="redis://localhost:6379"
# oppure per Upstash Redis:
# export REDIS_HOSTS="rediss://default:password@host.upstash.io:6379"

# Google Books API Key
export BOOK_API_KEY="your_google_books_api_key"

# Brevo API Key
export BREVO_API_KEY="your_brevo_api_key"

# Configurazione CORS (URL del frontend)
export HTTP_CORS_ORIGINS="http://localhost:5173"

# Porta dell'applicazione (opzionale, default 8080)
export PORT=8080
```

**Nota per Windows**: sostituire `export` con `set` e salvare il file come `.bat`:

```batch
set MONGODB_CONNECTION_STRING=mongodb+srv://...
set REDIS_HOSTS=redis://localhost:6379
...
```

### In locale

Per avviare l'applicazione in modalità di sviluppo locale:

1. **Configurare le variabili d'ambiente**: Eseguire lo script di configurazione

```bash
# Linux/Mac
source set-env.sh

# Windows
set-env.bat
```

2. **Avvio in modalità sviluppo**: Eseguire il comando per avviare Quarkus con live reload

```bash
./mvnw quarkus:dev
```

L'applicazione sarà accessibile all'indirizzo http://localhost:8080

La Dev UI di Quarkus sarà disponibile all'indirizzo http://localhost:8080/q/dev/

3. **Compilazione**: Per generare il pacchetto eseguibile

```bash
./mvnw package
```

Questo comando produce il file `quarkus-run.jar` nella directory `target/quarkus-app/`.

4. **Esecuzione del pacchetto compilato**:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

### Docker

Il progetto include un Dockerfile multi-stage che compila l'applicazione e crea un'immagine runtime ottimizzata.

#### Struttura del Dockerfile

```dockerfile
# Stage 1: Compilazione con Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN mvn package -DskipTests -Dquarkus.package.type=fast-jar

# Stage 2: Immagine runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /usr/app
COPY --from=build /usr/src/app/target/quarkus-app/ /usr/app/
EXPOSE 8080
CMD ["java", "-Dquarkus.http.port=${PORT}", "-jar", "quarkus-run.jar"]
```

#### Build e avvio con Docker

1. **Costruzione dell'immagine**: Le variabili d'ambiente devono essere passate al container in fase di esecuzione, non in fase di build

```bash
docker build -t bibliomap-api .
```

2. **Avvio del container**: Passare tutte le variabili d'ambiente necessarie

```bash
docker run -d \
  -p 8080:8080 \
  -e MONGODB_CONNECTION_STRING="mongodb+srv://..." \
  -e REDIS_HOSTS="redis://..." \
  -e BOOK_API_KEY="..." \
  -e BREVO_API_KEY="..." \
  -e HTTP_CORS_ORIGINS="http://localhost:5173" \
  --name bibliomap-api \
  bibliomap-api
```

#### Alternativa con Docker Compose

Per semplificare la gestione delle variabili d'ambiente, è possibile creare un file `docker-compose.yml`:

```yaml
version: "3.8"

services:
  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      MONGODB_CONNECTION_STRING: ${MONGODB_CONNECTION_STRING}
      REDIS_HOSTS: ${REDIS_HOSTS}
      BOOK_API_KEY: ${BOOK_API_KEY}
      BREVO_API_KEY: ${BREVO_API_KEY}
      HTTP_CORS_ORIGINS: ${HTTP_CORS_ORIGINS}
      PORT: 8080
    env_file:
      - .env
```

E un file `.env` nella root del progetto:

```env
MONGODB_CONNECTION_STRING=mongodb+srv://...
REDIS_HOSTS=redis://...
BOOK_API_KEY=...
BREVO_API_KEY=...
HTTP_CORS_ORIGINS=http://localhost:5173
```

Avvio con Docker Compose:

```bash
docker-compose up -d
```

### Verifica dell'installazione

Una volta avviata l'applicazione, verificare che sia operativa:

1. **Health check**: http://localhost:8080/q/health
2. **Swagger UI**: http://localhost:8080/q/swagger-ui
3. **Specifica OpenAPI**: http://localhost:8080/q/openapi

### Troubleshooting

#### Problema: Errore di connessione a MongoDB

- Verificare che la stringa di connessione sia corretta
- Controllare che l'IP sia nella whitelist di MongoDB Atlas
- Verificare le credenziali di accesso

#### Problema: Errore di connessione a Redis

- Verificare che Redis sia in esecuzione
- Controllare la stringa di connessione (redis:// vs rediss://)
- Per Upstash, verificare che la password sia corretta

#### Problema: CORS errors dal frontend

- Verificare che `HTTP_CORS_ORIGINS` contenga l'URL esatto del frontend
- Controllare il file `application.properties` per altre configurazioni CORS

#### Problema: Email non inviate

- Verificare la configurazione di Brevo API Key
- Controllare i log per eventuali errori di autenticazione
- Verificare che `email.debug-mode` sia impostato su `false`

## Dipendenze

Il progetto è basato sul framework **Quarkus** e utilizza le seguenti estensioni e tecnologie per garantire scalabilità, sicurezza e documentazione automatica:

- **[Quarkus REST (Jackson)](https://quarkus.io/guides/resteasy-reactive)**
  Implementazione Jakarta REST (precedentemente RESTEasy Reactive) per la gestione degli endpoint API.
- **[MongoDB with Panache](https://quarkus.io/guides/mongodb-panache)**
  Strumento di astrazione per MongoDB che semplifica l'accesso ai dati tramite il pattern Repository/Active Record.
- **[Redis Client](https://quarkus.io/guides/redis)**
  Client per l'integrazione con Redis, utilizzato per la gestione delle sessioni utente.
- **[Mailer](https://quarkus.io/guides/mailer)**
  Servizio per l'invio di email tramite protocollo SMTP.
- **[Qute Templating](https://quarkus.io/guides/qute)**
  Motore di templating type-safe utilizzato per generare dinamicamente il contenuto delle email.
- **[Hibernate Validator](https://quarkus.io/guides/validation)**
  Implementazione di Jakarta Bean Validation per validare automaticamente i dati in ingresso e garantire l'integrità dei parametri dei metodi.
- **[SmallRye OpenAPI](https://quarkus.io/guides/openapi-swaggerui)**
  Generazione automatica della specifica OpenAPI 3 e integrazione di **Swagger UI** per testare visivamente gli endpoint.
- **[SmallRye JWT Build](https://quarkus.io/guides/signed-jwt)**
  Libreria utilizzata per la creazione, firma e gestione dei JWT.
- **[SmallRye Fault Tolerance](https://quarkus.io/guides/fault-tolerance)**
  Fornisce pattern di resilienza come Timeout, Retry e Circuit Breaker per gestire in sicurezza le chiamate a servizi esterni (es. Google Books API).
- **[Scheduler](https://quarkus.io/guides/scheduler)**
  Servizio per la pianificazione di task automatici, utilizzato per gestire i promemoria dei prestiti in scadenza.
- **[Elytron Security](https://quarkus.io/guides/security-properties)**
  Infrastruttura di sicurezza per la gestione di identità e permessi basata su file di configurazione e standard Jakarta Security.
- **[OpenPDF](https://github.com/LibrePDF/OpenPDF)** (Libreria esterna)
  Libreria Java per la creazione e manipolazione di file PDF.

## MONGO DB

![Schema Database](./docs/imgs/db_schema.jpg)

Spiegazione dei collegamenti:

USER & LOCATION: Ogni utente ha un locationId che punta alle coordinate geografiche della propria zona/casa.

LIBRARY & USER: Una libreria appartiene a un utente (ownerId). Un utente può avere più librerie (relazione 1 a molti).

LIBRARY & LOCATION: Ogni libreria ha il proprio locationId (che può essere diverso da quello dell'utente).

COPY & BOOK: La COPY (Copia Fisica) è l'istanza reale di un BOOK (Titolo/ISBN). Molte copie diverse possono fare riferimento allo stesso ISBN.

COPY & LIBRARY: Ogni copia fisica è depositata all'interno di una specifica libreria (libraryId).

LOAN (Prestito): È l'oggetto "centrale" che mette in relazione:

    - La copia fisica specifica che viene scambiata (copy_id).
    - L'utente che presta il libro (owner_id).
    - L'utente che chiede il libro (requester_id).

### ESEMPIO UTENTE

```json
{
  "_id": "b53f4a78-437b-4657-b9e0-b75b5e05c2c2",
  "acceptedTerms": true,
  "blurRadius": 100,
  "createdAt": {
    "$date": "2025-12-23T15:59:45.947Z"
  },
  "email": "gandalf@middlearth.com",
  "hashedPassword": "$2a$10$9W2txKtJJCyyMEnCinatvu05WcWy2qMvtSCHYT9/IowVeyR71szbe",
  "history": [
    {
      "field": "username",
      "action": "USERNAME_UPDATED",
      "from": "gandalf_the_grey",
      "to": "gandalf_the_white",
      "changedOn": {
        "$date": "2025-12-28T11:44:12.840Z"
      }
    },
    {
      "field": "visibility",
      "action": "VISIBILITY_UPDATED",
      "from": "logged-in",
      "to": "all",
      "changedOn": {
        "$date": "2025-12-28T11:45:27.059Z"
      }
    },
    {
      "field": "blurRadius",
      "action": "BLUR_RADIUS_UPDATED",
      "from": "220",
      "to": "220",
      "changedOn": {
        "$date": "2025-12-28T11:45:27.060Z"
      }
    }
  ],
  "locationId": "1e2437c9-aaa1-4bf1-8edf-e4b53781bd51",
  "modifiedAt": {
    "$date": "2025-12-28T13:34:29.988Z"
  },
  "username": "gandalf_the_white",
  "visibility": "all"
}
```

### ricerca punto vicino

```code
db.locations.aggregate([
  {
    $geoNear: {
      near: {
        type: "Point",
        coordinates: [12.477304088827793, 41.890917165114445]
      },
      distanceField: "distance",
      maxDistance: 16021.674122191978,
      spherical: true
    }
  },
  {
    $lookup: {
      from: "libraries",
      localField: "_id",
      foreignField: "locationId",
      as: "library"
    }
  },
  {
    $unwind: "$library"
  },
  {
    $match: {
      "library.visibility": { $in: ["all"] },
      "library.ownerId": { $ne: "1e496285-8361-4887-b08e-2dff78e391fe" }
    }
  },
  {
    $lookup: {
      from: "users",
      localField: "library.ownerId",
      foreignField: "_id",
      as: "ownerInfo"
    }
  },
  {
    $unwind: {
      path: "$ownerInfo",
      preserveNullAndEmptyArrays: true
    }
  },
  {
    $lookup: {
      from: "copies",
      localField: "library._id",
      foreignField: "libraryId",
      as: "copy"
    }
  },
  {
    $unwind: "$copy"
  },
  {
    $lookup: {
      from: "books",
      localField: "copy.book_isbn",
      foreignField: "_id",
      as: "bookInfo"
    }
  },
  {
    $unwind: "$bookInfo"
  },
  {
    $sort: {
      distance: 1
    }
  }
])
```

Questa pipeline esegue una ricerca di posizioni geografiche vicine a un punto dato e costruisce un risultato arricchito tramite join su piu collezioni correlate.

In sintesi la pipeline:

1. Cerca le location entro una distanza massima dal punto geografico specificato
2. Calcola la distanza di ogni location dal punto di partenza
3. Collega ogni location alle librerie associate
4. Filtra le librerie visibili a tutti ed esclude quelle appartenenti a uno specifico proprietario
5. Recupera le informazioni del proprietario della libreria
6. Recupera le copie dei libri presenti in ciascuna libreria
7. Recupera le informazioni dei libri associati alle copie
8. Ordina il risultato finale per distanza crescente dalla posizione di partenza

Il risultato e' un elenco di libri disponibili in librerie vicine con informazioni complete su location libreria proprietario e libro ordinate per prossimita' geografica.

### Inizializzazione e Setup Locale

Sebbene l'applicazione sia configurata per connettersi a un cluster MongoDB Atlas remoto, vengono forniti gli script necessari per replicare l'intera struttura del database e i dati di test in un ambiente locale.

Gli script di manutenzione si trovano nel percorso docs/scripts.

Struttura della directory:

```bash
.
├── bibliomap.drawio
├── imgs
│   └── db_schema.jpg
├── openapi.yaml
└── scripts
    ├── bibliomap-db-init.js       # Script di definizione schema e indici
    ├── bibliomap-db-populate.js   # Script di importazione dati
    └── json                       # Dataset in formato JSON
        ├── bibliomap.books.json
        ├── bibliomap.copies.json
        ├── bibliomap.libraries.json
        ├── bibliomap.loans.json
        ├── bibliomap.locations.json
        └── bibliomap.users.json
```

### Logica e Ordine di Esecuzione

Per garantire la corretta inizializzazione degli indici geospaziali (necessari per le query $geoNear) e l'integrità referenziale dei dati, è fondamentale rispettare l'ordine di esecuzione degli script.

1. bibliomap-db-init.js: Questo script si occupa della definizione strutturale. Crea le collection, applica le regole di validazione (JSON Schema Validator) e, soprattutto, costruisce l'indice 2dsphere sulla collection locations.

2. bibliomap-db-populate.js: Questo script legge i dataset presenti nella sottocartella /json e popola il database.

Procedura di avvio (tramite mongosh):

```bash
# 1. Spostarsi nella directory degli script
cd docs/scripts

# 2. Inizializzare lo schema e gli indici
mongosh bibliomap-db-init.js

# 3. Popolare il database con i dati di test
mongosh bibliomap-db-populate.js
```

## TODO

- [x] controllo su click multiplo per get otp
- [x] accedi
- [x] controllo visualizzazione messaggio mock
- [x] email fatta meglio
  - [x] aggiungi css coerente al fe alle mail
- [x] reinvio otp
- [x] salvataggio dati location
- [x] riorganizza registration dto, usa solo uno con valori nullable
- [x] usa error message sempre nei casi di errore (no mappa)
- [x] crea oggetti model per Books, Copies, Locations e Libraries
- [x] per le cover usiamo anche google books, da togliere com'è ora nel fe
- [x] definisci flusso di prestito
- [x] per i prestiti se ce ne sono più pending per la stessa cosa gli altri vengono automaticamente rifiutati
- [x] pagina aggiunta libro (da finire e testare), deve avere:
  - [x] controllo su google books
  - [x] inserimento isbn
  - [x] sblocco camera per isbn
  - [x] condizione ma scelta fissa (nuovo/ottima/discreta/usato/pessima)
  - [x] caricamento copertina (drag or choose, cerca su google, camera, scegli icona)
  - [x] lista di tag (scegli da lista fissa, suggeriti dal libro o nuovo tag)
  - [x] da definire logica tag e copertina
  - [x] GET /api/users/me/libraries
    - [x] recupera lista librerie dell'utente loggato (id, nome) per menu a tendina
  - [x] GET /api/books/external/lookup-metadata
  - [x] GET /api/books/external/search-isbn
- [x] pagina libro per proprietario con modifiche abilitate (solo proprietà della copia modificabili, copertina forse diventa proprietà copia)
- [x] gestione libri, sposta da libreria a libreria
- [x] pagina libro per utente, bottone per richiedere prestito -> manda email
- [x] libreria: aggiungere gestione location propria
- [x] libreria: possibilità di visibilità diversa da utente
- [x] fuzzy location
- [x] creare Enum LoanStatus: PENDING, ACCEPTED, ON_LOAN, RETURNED, REJECTED, CANCELLED
- [x] creare Entity Loan nella collection loans
- [x] creare LoanRepository
- [x] implementare createLoanRequest(requesterId, copyId):
  - [x] verifica copia esista e status == 'available'
  - [x] salva Loan con stato PENDING
  - [x] invia email notifica al proprietario
- [x] implementare manageRequest(loanId, ownerId, action):
  - [x] action ACCEPT: aggiorna stato a ACCEPTED, notifica richiedente
  - [x] action REJECT: aggiorna stato a REJECTED, notifica richiedente
- [x] implementare startLoan(loanId, ownerId) (consegna fisica):
  - [x] verifica stato sia ACCEPTED
  - [x] aggiorna Loan: stato ON_LOAN, loanStartDate = now, expectedReturnDate = now + 30gg
  - [x] side effect: aggiorna Copy: status = on_loan
- [x] implementare closeLoan(loanId, ownerId, conditionEnd) (restituzione):
  - [x] verifica stato sia ON_LOAN
  - [x] aggiorna Loan: stato RETURNED, actualReturnDate = now, conditionEnd
  - [x] side effect: aggiorna Copy: status = available, condition = conditionEnd
- [x] POST /api/loans/request: Body { copyId }
- [x] PATCH /api/loans/{id}/status: Body { status: 'ACCEPTED'|'REJECTED' }
- [x] POST /api/loans/{id}/start: endpoint per segnare inizio prestito
- [x] POST /api/loans/{id}/return: Body { condition: '...' }
- [x] GET /api/loans/requests/incoming: richieste da approvare
- [x] GET /api/loans/active: prestiti in corso (dati ricevuti e dati propri)
- [x] creare Scheduler @Scheduled(cron = "0 0 9 \* \* ?") (ogni giorno alle 9):
  - [x] cerca prestiti scaduti (ON_LOAN && expectedReturnDate passata)
  - [x] per ogni prestito invia email di sollecito al requesterId
- [x] scarica swagger yaml (http://localhost:8080/q/openapi) per doc
- [x] documentazione
- [x] documentazione docker
- [x] script mongo
- [x] COOKIE PARTITIONED controllo anche su mobile e altri browser
- [x] url reali ovunque
  - [x] controlla conf email
  - [x] controlla swagger
  - [x] frontend
- [x] cambia pwd degli utenti demo
- [x] aggiorna json mongo
- [ ] rel
