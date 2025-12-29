# bibliomap-api

Questo progetto contiene il backend dell'applicazione Bibliomap. Il codice è sviluppato in Java e utilizza il framework Quarkus per esporre le API necessarie al funzionamento del client.

## INDEX
 - [Documentazione API](#documentazione-api)
 - [Quarkus](#sviluppo-con-quarkus)
 - [Dipendenze](#dipendenze-e-tecnologie)
 - [TODO](#todo)

## Documentazione API

La specifica tecnica delle API è disponibile visualizzando il file [swagger.yaml](swagger.yaml).

## Sviluppo con Quarkus

Questo progetto utilizza Quarkus. Per approfondimenti visitare il sito ufficiale https://quarkus.io/

### Esecuzione in modalità sviluppo

È possibile avviare l'applicazione in modalità sviluppo per abilitare il live coding con il comando:
  
   ```shell
    ./mvnw quarkus:dev
   ```

La Dev UI è disponibile solo in questa modalità all'indirizzo http://localhost:8080/q/dev/

### Compilazione e pacchettizzazione

L'applicazione può essere impacchettata usando:

   ```shell
    ./mvnw package
   ```

Questo comando produce il file `quarkus-run.jar` nella directory `target/quarkus-app/`.
Le dipendenze vengono copiate nella directory `target/quarkus-app/lib/`.

Per avviare l'applicazione usare il comando:

   ```shell
    java -jar target/quarkus-app/quarkus-run.jar
   ```

Se si desidera costruire un über-jar (un singolo file contenente tutto) eseguire:

   ```shell
    ./mvnw package -Dquarkus.package.jar.type=uber-jar
   ```

L'applicazione sarà avviabile con `java -jar target/*-runner.jar`.


## Dipendenze e Guide correlate

Il progetto fa uso delle seguenti estensioni e tecnologie:

* **REST**
  Implementazione Jakarta REST con elaborazione a tempo di compilazione basata su Vert.x.
* **MongoDB client**
  Connettore per database MongoDB in stile imperativo o reattivo.
* **Micrometer Registry Prometheus**
  Abilita il supporto Prometheus per la raccolta metriche con Micrometer.
* **Hibernate Validator**
  Validazione delle proprietà degli oggetti e dei parametri dei metodi.
* **SmallRye OpenAPI**
  Documentazione delle API REST (include Swagger UI).
* **SmallRye Fault Tolerance**
  Libreria per la gestione della tolleranza ai guasti nei servizi di rete.
* **RESTEasy Classic Multipart**
  Supporto per le richieste Multipart.
* **YAML Configuration**
  Permette l'uso di file YAML per la configurazione dell'applicazione.
* **SmallRye JWT**
  Sicurezza delle applicazioni tramite JSON Web Token.
* **SmallRye Health**
  Monitoraggio dello stato di salute del servizio.
* **SmallRye Metrics**
  Esposizione delle metriche di servizio.
* **SmallRye JWT Build**
  API per la creazione e firma di token JWT.

## MONGO DB (todo da spostare in doc totale)

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
    },
    {
      "field": "blurRadius",
      "action": "BLUR_RADIUS_UPDATED",
      "from": "220",
      "to": "100",
      "changedOn": {
        "$date": "2025-12-28T12:34:29.988Z"
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
  
## TODO
 - [x] controllo su click multiplo per get otp
 - [x] accedi
 - [x] controllo visualizzazione messaggio mock
 - [x] email fatta meglio 
    - [ ] aggiungi css coerente al fe alla mail
- [ ] reinvio otp
- [x] salvataggio dati location
- [x] riorganizza registration dto, usa solo uno con valori nullable
- [x] usa error message sempre nei casi di errore (no mappa)
- [x] crea oggetti model per Books, Copies, Locations e Libraries
- [ ] per le cover usiamo anche google books, da togliere com'è ora nel fe
- [ ] definisci flusso di ricerca
- [ ] definisci flusso di prestito
- [ ] pagina aggiunta libro (da finire e testare), deve avere:
 - [x] controllo su google books
 - [x] inserimento isbn
 - [x] sblocco camera per isbn
 - [ ] condizione opzionale ma scelta fissa (da definire nuovo/ottima/discreta/usato/pessima)
 - [x] caricamento copertina (drag or choose, cerca su google, camera??, scegli icona)
 - [x] lista di tag (si può scegliere da una lista fissa, alcuni suggeriti dal libro stesso, oppure inserire un tag nuovo)
 - [ ] da definire bene logica tag e copertina
 - [x] GET /api/users/me/libraries
  - [x] recupera lista librerie dell'utente loggato (id, nome) per il menu a tendina
 - [x] GET /api/books/external/lookup-metadata
 - [x] GET /api/books/external/search-isbn
 - [ ] POST /api/books/add-composite
- [x] pagina libro per proprietario con modifiche abilitate (gestione copia/libro, modificabili solo proprietà della copia, la copertina forse diventa proprietà della copia)
- [ ] gestione libri, sposta da libreria a libreria, duplica
- [x] pagina libro per utente che la vede, bottone per richiedere il prestito -> manda email
- [ ] libreria da aggiungere la gestione di una location sua
- [ ] libreria va messa la possibilità di una visibilità diversa da quella dell'utente
- [ ] fuzzy level (da ricontrollare se è vero)
- [x] Creare Enum `LoanStatus`: `PENDING`, `ACCEPTED`, `ON_LOAN`, `RETURNED`, `REJECTED`, `CANCELLED`
- [x] Creare Entity `Loan` nella collection `loans`:
- [x] Creare `LoanRepository`:
- [x] Implementare `createLoanRequest(requesterId, copyId)`:
    - Verifica che la copia esista e `status == 'available'`
    - Salva `Loan` con stato `PENDING`
    - Invia Email notifica al proprietario
- [x] Implementare `manageRequest(loanId, ownerId, action)`:
    - Action `ACCEPT`: aggiorna stato a `ACCEPTED`, notifica richiedente
    - Action `REJECT`: aggiorna stato a `REJECTED`, notifica richiedente
- [x] Implementare `startLoan(loanId, ownerId)` (Consegna fisica):
    - Verifica che stato sia `ACCEPTED`
    - Aggiorna `Loan`: stato `ON_LOAN`, `loanStartDate` = now, `expectedReturnDate` = now + 30gg
    - **SIDE EFFECT**: Aggiorna `Copy`: `status` = `on_loan`
- [x] Implementare `closeLoan(loanId, ownerId, conditionEnd)` (Restituzione):
    - Verifica che stato sia `ON_LOAN`
    - Aggiorna `Loan`: stato `RETURNED`, `actualReturnDate` = now, `conditionEnd`
    - **SIDE EFFECT**: Aggiorna `Copy`: `status` = `available`, `condition` = `conditionEnd`
- [x] `POST /api/loans/request`: Body `{ copyId }`
- [x] `PATCH /api/loans/{id}/status`: Body `{ status: 'ACCEPTED'|'REJECTED' }`
- [x] `POST /api/loans/{id}/start`: Endpoint per segnare l'inizio del prestito
- [x] `POST /api/loans/{id}/return`: Body `{ condition: '...' }`
- [x] `GET /api/loans/requests/incoming`: Richieste da approvare
- [x] `GET /api/loans/active`: Prestiti in corso (sia dati che ricevuti)
- [x] Creare Scheduler `@Scheduled(cron = "0 0 9 * * ?")` (ogni giorno alle 9):
    - Cerca prestiti scaduti (`ON_LOAN` && `expectedReturnDate` passata)
    - Per ogni prestito, invia Email di sollecito al `requesterId`
- [ ] scarica swagger yaml (http://localhost:8080/q/openapi) per inserirlo nella doc
- [ ] documentazione
- [ ] crea email gmail apposita con foto profilo bibliomap da usare al posto di quella universitaria
