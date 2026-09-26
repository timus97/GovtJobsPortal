package in.govtjobs.web.store;

public class StoreException extends RuntimeException {
    private final String code;

    public StoreException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
