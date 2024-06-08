package hu.gds.ldap4j.ldap;

import java.io.Serial;

public class UnexpectedMessageIdException extends RuntimeException {
    @Serial
    private static final long serialVersionUID=0L;

    public UnexpectedMessageIdException(String message) {
        super(message);
    }
}
