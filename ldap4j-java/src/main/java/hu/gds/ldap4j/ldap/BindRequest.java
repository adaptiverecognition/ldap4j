package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record BindRequest(
        @NotNull AuthenticationChoice authentication,
        @NotNull String name,
        int version) {
    public sealed interface AuthenticationChoice {
        record SASL(
                byte[] credentials,
                @NotNull String mechanism)
                implements AuthenticationChoice {
            public SASL(byte[] credentials, @NotNull String mechanism) {
                this.credentials=credentials;
                this.mechanism=Objects.requireNonNull(mechanism, "mechanism");
            }

            @Override
            public void check(@NotNull LdapResult ldapResult) throws Throwable {
                ldapResult.checkSASL();
            }

            @Override
            public @NotNull ByteBuffer write() throws Throwable {
                ByteBuffer saslBuffer=DER.writeUtf8Tag(mechanism);
                if (null!=credentials) {
                    saslBuffer=saslBuffer.append(
                            DER.writeTag(
                                    DER.OCTET_STRING,
                                    ByteBuffer.create(credentials)));
                }
                return DER.writeTag(
                        Ldap.AUTHENTICATION_CHOICE_SASL,
                        saslBuffer);
            }
        }

        record Simple(
                char[] password)
                implements AuthenticationChoice {
            @Override
            public void check(@NotNull LdapResult ldapResult) throws Throwable {
                ldapResult.check();
            }

            @Override
            public @NotNull ByteBuffer write() throws Throwable {
                return DER.writeTag(
                        Ldap.AUTHENTICATION_CHOICE_SIMPLE,
                        DER.writeUtf8NoTag(password));
            }
        }

        void check(@NotNull LdapResult ldapResult) throws Throwable;

        @NotNull ByteBuffer write() throws Throwable;
    }

    public BindRequest(@NotNull AuthenticationChoice authentication, @NotNull String name, int version) {
        this.authentication=Objects.requireNonNull(authentication, "authentication");
        this.name=Objects.requireNonNull(name, "name");
        this.version=version;
    }

    public static @NotNull BindRequest sasl(
            byte[] credentials, @NotNull String mechanism, @NotNull String name) {
        return new BindRequest(new AuthenticationChoice.SASL(credentials, mechanism), name, Ldap.VERSION);
    }

    public static @NotNull BindRequest simple(@NotNull String name, char[] password) {
        return new BindRequest(new AuthenticationChoice.Simple(password), name, Ldap.VERSION);
    }

    public @NotNull ByteBuffer write() throws Throwable {
        return DER.writeTag(
                Ldap.PROTOCOL_OP_BIND_REQUEST,
                DER.writeIntegerTag(false, version)
                        .append(DER.writeUtf8Tag(name))
                        .append(authentication.write()));
    }
}
