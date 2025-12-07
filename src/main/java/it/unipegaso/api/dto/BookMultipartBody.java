package it.unipegaso.api.dto;

import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public class BookMultipartBody {

	@RestForm("book")
    @PartType(MediaType.APPLICATION_JSON)
    public BookDetailDTO book;

    @RestForm("cover")
    public FileUpload coverFile;
}
