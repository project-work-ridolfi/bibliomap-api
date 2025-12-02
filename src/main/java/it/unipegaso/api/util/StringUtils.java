package it.unipegaso.api.util;

import java.util.Locale;

public class StringUtils {
	
	private StringUtils() {
		//costruttore per nascondere quello automatico
	}
	
	
	public static String getFullLanguage(String abbr) {
		
		if (abbr != null && !abbr.isBlank()) {
		    // crea un locale basato sul codice arrivato (es. "it", "en")
		    Locale sourceLocale = Locale.forLanguageTag(abbr);
		    
		    // ottiene il nome della lingua visualizzato in italiano (es. "italiano", "inglese")
		    String displayLanguage = sourceLocale.getDisplayLanguage(Locale.ITALIAN);
		    
		    // java restituisce "italiano" minuscolo, il tuo frontend vuole "Italiano"
		    if (!displayLanguage.isEmpty()) {
		        String capitalized = displayLanguage.substring(0, 1).toUpperCase() + displayLanguage.substring(1);
		        return capitalized;
		    }
		}
		
		return abbr;
	}

}
