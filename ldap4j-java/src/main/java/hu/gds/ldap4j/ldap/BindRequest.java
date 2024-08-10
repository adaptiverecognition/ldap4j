package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record BindRequest(
        @NotNull AuthenticationChoice authentication,
        @NotNull String name,
        int version)
        implements Request<BindRequest, BindResponse> {
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
            public @NotNull MessageReader<BindResponse> responseReader() {
                return BindResponse.READER_SASL;
            }

            @Override
            public @NotNull ByteBuffer write() {
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
            public @NotNull MessageReader<BindResponse> responseReader() {
                return BindResponse.READER_SUCCESS;
            }

            @Override
            public @NotNull ByteBuffer write() {
                return DER.writeTag(
                        Ldap.AUTHENTICATION_CHOICE_SIMPLE,
                        DER.writeUtf8NoTag(password));
            }
        }

        @NotNull MessageReader<BindResponse> responseReader();

        @NotNull ByteBuffer write();
    }

    public BindRequest(
            @NotNull AuthenticationChoice authentication,
            @NotNull String name,
            int version) {
        this.authentication=Objects.requireNonNull(authentication, "authentication");
        this.name=Objects.requireNonNull(name, "name");
        this.version=version;
    }

    @Override
    public @NotNull MessageReader<BindResponse> responseReader() {
        return authentication().responseReader();
    }

    public static @NotNull BindRequest sasl(
            byte[] credentials, @NotNull String mechanism, @NotNull String name) {
        return new BindRequest(
                new AuthenticationChoice.SASL(credentials, mechanism),
                name,
                Ldap.VERSION);
    }

    @Override
    public @NotNull BindRequest self() {
        return this;
    }

    public static @NotNull BindRequest simple(@NotNull String name, char[] password) {
        return new BindRequest(
                new AuthenticationChoice.Simple(password),
                name,
                Ldap.VERSION);
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.bindRequest(this);
    }

    @Override
    public @NotNull ByteBuffer write() {
        return DER.writeTag(
                Ldap.PROTOCOL_OP_BIND_REQUEST,
                DER.writeIntegerTag(version)
                        .append(DER.writeUtf8Tag(name))
                        .append(authentication.write()));
    }
}
