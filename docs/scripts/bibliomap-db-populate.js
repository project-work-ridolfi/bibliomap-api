/**
 * ====================================================================================
 *  BIBLIOMAP - POPULATE DATABASE SCRIPT
 * ====================================================================================
 *
 * DESCRIZIONE:
 * Questo script gestisce il popolamento massivo delle collection MongoDB importando
 * dataset strutturati da file JSON esterni. La procedura e' progettata per garantire
 * il caricamento consistente dei dati di test, rispettando i vincoli di schema
 * e l'integrita' referenziale logica definita nel modello dati.
 *
 * DIPENDENZE E PREREQUISITI:
 * 1. Inizializzazione Schema: L'esecuzione di questo script e' subordinata al
 * precedente lancio di 'bibliomap-db-init.js'. La pre-esistenza degli indici
 * (in particolare l'indice spaziale '2dsphere' sulla collection 'locations')
 * e' condizione necessaria per la corretta persistenza dei dati GeoJSON.
 *
 * 2. Dataset di Riferimento: I file JSON contenenti i dati strutturati necessari
 * per il popolamento sono allocati nella directory relativa './json', fornita
 * unitamente agli script di gestione del database.
 *
 * CONTESTO DI ESECUZIONE:
 * Lo script utilizza la risoluzione relativa dei percorsi (CWD). Pertanto,
 * l'interprete 'mongosh' deve essere invocato mantenendo la directory dello
 * script come working directory corrente.
 *
 * UTILIZZO:
 * $ mongosh bibliomap-db-populate.js
 * ====================================================================================
 */

// Moduli Node.js standard per gestione file e percorsi (Cross-Platform)
const fs = require('fs');
const path = require('path');

const dbName = "bibliomap";
const database = db.getSiblingDB(dbName);

// Ottiene il percorso corrente di esecuzione dello script
const currentDir = process.cwd();
const jsonDir = path.join(currentDir, 'json'); 

print("\n========================================================");
print(" AVVIO POPOLAMENTO DATI BIBLIOMAP");
print("========================================================");
print(" Database target : " + dbName);
print(" Cartella dati   : " + jsonDir);

// Verifica esistenza cartella 'json'
if (!fs.existsSync(jsonDir)) {
    print("\n[ERRORE CRITICO] La cartella 'json' non è stata trovata.");
    print("Assicurati di lanciare questo script posizionandoti nella sua cartella.");
    print("Percorso cercato: " + jsonDir);
    quit(); // Interrompe l'esecuzione
}

// Funzione di importazione generica per una collection
function importCollection(collectionName, fileName) {
    // path.join gestisce automaticamente i separatori \ o /
    const filePath = path.join(jsonDir, fileName);
    
    try {
        if (!fs.existsSync(filePath)) {
            print(" [WARN] File mancante: " + fileName + " -> Saltato.");
            return;
        }

        const fileContent = fs.readFileSync(filePath, 'utf8');
        // Parsifica il JSON. Si aspetta un Array di oggetti.
        const data = JSON.parse(fileContent);

        if (Array.isArray(data) && data.length > 0) {
            const col = database.getCollection(collectionName);
            const result = col.insertMany(data);

            const count = Object.keys(result.insertedIds).length;
            
            print(" [OK] " + collectionName.padEnd(12) + ": Inseriti " + count + " documenti.");
        } else {
            print(" [INFO] " + fileName + " e' vuoto -> Nessun dato inserito.");
        }

    } catch (error) {
        print(" [ERROR] Fallimento importazione " + collectionName + ": " + error.message);
    }
}

// --------------------------------------------------------
// ESECUZIONE IMPORTAZIONI
// --------------------------------------------------------
importCollection("users",     "bibliomap.users.json");
importCollection("libraries", "bibliomap.libraries.json");
importCollection("books",     "bibliomap.books.json");
importCollection("copies",    "bibliomap.copies.json");
importCollection("loans",     "bibliomap.loans.json");
importCollection("locations", "bibliomap.locations.json");

print("\n========================================================");
print(" POPOLAMENTO TERMINATO");
print("========================================================");