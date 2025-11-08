package it.unipegaso.service.otp;

public class CounterStrategy {
    public long getCounter() {
        return System.currentTimeMillis() / 30000;
    }
}
