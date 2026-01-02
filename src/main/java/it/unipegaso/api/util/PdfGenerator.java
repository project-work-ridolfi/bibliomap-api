 package it.unipegaso.api.util;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import io.quarkus.logging.Log;

public class PdfGenerator {

    public static byte[] generateUserExportPdf(Map<String, Object> allData) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Aggiunti margini più ampi (50 unità)
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, out);
            document.open();

            Color zompColor = new Color(98, 150, 119);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, zompColor);
            Font secFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, zompColor);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            // 1. HEADER PRINCIPALE
            Paragraph title = new Paragraph("REPORT DATI UTENTE BIBLIOMAP", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);
            
            Paragraph dateGen = new Paragraph("Esportazione generata il: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date()), smallFont);
            dateGen.setAlignment(Element.ALIGN_CENTER);
            dateGen.setSpacingAfter(20);
            document.add(dateGen);
            
            document.add(new LineSeparator());

            // 2. DATI ACCOUNT
            Paragraph accountTitle = new Paragraph("\nDATI ACCOUNT", secFont);
            accountTitle.setSpacingBefore(15);
            accountTitle.setSpacingAfter(10);
            document.add(accountTitle);

            Map<String, Object> user = (Map<String, Object>) allData.get("user");
            document.add(new Paragraph("Username: " + user.get("username"), smallFont));
            document.add(new Paragraph("Email: " + user.get("email"), smallFont));
            document.add(new Paragraph("Data Registrazione: " + user.get("createdAt"), smallFont));
            document.add(new Paragraph("Ultima Modifica: " + user.get("modifiedAt"), smallFont));
            document.add(new Paragraph("Impostazioni Privacy: " + user.get("visibility"), smallFont));
            
            // Fix Location Utente
            String locStr = "Non impostata o privata";
            if (user.containsKey("location") && user.get("location") != null) {
                Map<String, Double> loc = (Map<String, Double>) user.get("location");
                if (loc.get("latitude") != 0.0) {
                    locStr = "Lat " + loc.get("latitude") + " / Lon " + loc.get("longitude");
                }
            }
            document.add(new Paragraph("Coordinate Casa: " + locStr, smallFont));

            // 3. STORICO (Se presente)
            List<Map<String, Object>> history = (List<Map<String, Object>>) user.get("history");
            if (history != null && !history.isEmpty()) {
                Paragraph histTitle = new Paragraph("\nSTORICO MODIFICHE PROFILO", boldFont);
                histTitle.setSpacingBefore(20);
                document.add(histTitle);

                PdfPTable table = new PdfPTable(4);
                table.setWidthPercentage(100);
                table.setSpacingBefore(10);
                table.setHeaderRows(1); // Ripete l'header se va in nuova pagina
                
                addCell(table, "Campo", boldFont, true);
                addCell(table, "Azione", boldFont, true);
                addCell(table, "Modifica", boldFont, true);
                addCell(table, "Data", boldFont, true);
                
                SimpleDateFormat hSdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
                for (Map<String, Object> h : history) {
                    addCell(table, String.valueOf(h.get("field")), smallFont, false);
                    addCell(table, String.valueOf(h.get("action")), smallFont, false);
                    addCell(table, h.get("from") + " > " + h.get("to"), smallFont, false);
                    Object d = h.get("changedOn");
                    addCell(table, (d instanceof java.util.Date) ? hSdf.format(d) : String.valueOf(d), smallFont, false);
                }
                document.add(table);
            }

            // Forza nuova pagina per le librerie se lo spazio è poco
            document.add(new Paragraph("\n")); 

            // 4. LIBRERIE E LIBRI
            Paragraph libTitle = new Paragraph("\nLE TUE LIBRERIE", secFont);
            libTitle.setSpacingBefore(20);
            document.add(libTitle);

            List<Map<String, Object>> libraries = (List<Map<String, Object>>) allData.get("libraries");
            for (Map<String, Object> lib : libraries) {
                Paragraph lName = new Paragraph("\nLibreria: " + lib.get("name"), boldFont);
                lName.setSpacingBefore(10);
                document.add(lName);
                
                document.add(new Paragraph("Creata il: " + lib.get("createdAt"), smallFont));

                PdfPTable bTable = new PdfPTable(4);
                bTable.setWidthPercentage(100);
                bTable.setSpacingBefore(8);
                bTable.setSpacingAfter(15);
                bTable.setHeaderRows(1);
                bTable.setKeepTogether(true); // Cerca di non spezzare la tabella tra pagine

                addCell(bTable, "Titolo Libro", boldFont, true);
                addCell(bTable, "Autore / Anno", boldFont, true);
                addCell(bTable, "Condizione", boldFont, true);
                addCell(bTable, "Stato", boldFont, true);

                for (Map<String, Object> b : (List<Map<String, Object>>) lib.get("books")) {
                    addCell(bTable, String.valueOf(b.get("title")), smallFont, false);
                    addCell(bTable, b.get("author") + " (" + b.get("year") + ")", smallFont, false);
                    addCell(bTable, String.valueOf(b.get("condition")), smallFont, false);
                    addCell(bTable, String.valueOf(b.get("status")), smallFont, false);
                }
                document.add(bTable);
            }

            // Nuova pagina per i prestiti per pulizia
            document.newPage();

            // 5. PRESTITI
            Paragraph loansTitle = new Paragraph("RIEPILOGO PRESTITI E SCAMBI", secFont);
            loansTitle.setSpacingAfter(15);
            document.add(loansTitle);

            document.add(new Paragraph("RICHIESTE EFFETTUATE (Libri che hai chiesto)", boldFont));
            addLoanTable(document, (List<Map<String, Object>>) allData.get("loansMade"), smallFont, boldFont);

            document.add(new Paragraph("\nRICHIESTE RICEVUTE (Tuoi libri prestati)", boldFont));
            addLoanTable(document, (List<Map<String, Object>>) allData.get("loansReceived"), smallFont, boldFont);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            Log.error("Errore fatale generazione PDF: ", e);
            throw new RuntimeException(e);
        }
    }

    private static void addLoanTable(Document doc, List<Map<String, Object>> loans, Font f, Font b) throws DocumentException {
        if (loans == null || loans.isEmpty()) {
            Paragraph p = new Paragraph("Nessuna operazione registrata.", f);
            p.setSpacingBefore(5);
            p.setSpacingAfter(10);
            doc.add(p);
            return;
        }
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        table.setSpacingAfter(20);
        table.setHeaderRows(1);

        addCell(table, "Titolo Libro", b, true);
        addCell(table, "Partner", b, true);
        addCell(table, "Scadenza", b, true);
        addCell(table, "Stato Attuale", b, true);

        for (Map<String, Object> l : loans) {
            addCell(table, String.valueOf(l.get("title")), f, false);
            addCell(table, String.valueOf(l.get("partner")), f, false);
            addCell(table, String.valueOf(l.get("expectedReturn")), f, false);
            addCell(table, String.valueOf(l.get("status")), f, false);
        }
        doc.add(table);
    }

    private static void addCell(PdfPTable table, String text, Font font, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setPadding(8); // Più spazio interno alle celle
        if (header) {
            cell.setBackgroundColor(new Color(235, 235, 235));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        }
        table.addCell(cell);
    }
}