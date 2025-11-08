package it.unipegaso.service.otp;

public interface IBaseStringEncoding {
    byte[] decode(String encoded) throws DecodingException;
    String encode(byte[] data);
}
