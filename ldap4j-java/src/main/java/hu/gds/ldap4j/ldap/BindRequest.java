package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BindRequest(
        @NotNull AuthenticationChoice authentication,
        @NotNull String name,
        int version)
        implements Request<BindRequest, BindResponse> {
    public sealed interface AuthenticationChoice {
        record SASL(
                byte@Nullable [] credentials,
                @NotNull String mechanism)
                implements AuthenticationChoice {
            public SASL(byte@Nullable[] credentials, @NotNull String mechanism) {
                this.credentials=credentials;
                this.mechanism=Objects.requireNonNull(mechanism, "mechanism");
            }

            @Override
            public @NotNull MessageReader<BindResponse> responseReader() {
                return BindResponse.READER_SASL;
            }

            @Override
            public @NotNull ByteBuffer write() {
                ByteBuffer saslBuffer=BER.writeUtf8Tag(mechanism);
                if (null!=credentials) {
                    saslBuffer=saslBuffer.append(
                            BER.writeTag(
                                    BER.OCTET_STRING,
                                    ByteBuffer.create(credentials)));
                }
                return BER.writeTag(
                        AUTHENTICATION_CHOICE_SASL_TAG,
                        saslBuffer);
            }
        }

        record Simple(
                char@NotNull[] password)
                implements AuthenticationChoice {
            public Simple(char @NotNull [] password) {
                this.password=Objects.requireNonNull(password, "password");
            }

            @Override
            public @NotNull MessageReader<BindResponse> responseReader() {
                return BindResponse.READER_SUCCESS;
            }

            @Override
            public @NotNull ByteBuffer write() {
                return BER.writeTag(
                        AUTHENTICATION_CHOICE_SIMPLE_TAG,
                        BER.writeUtf8NoTag(password));
            }
        }

        @NotNull MessageReader<BindResponse> responseReader();

        @NotNull ByteBuffer write();
    }
    
    public static final byte AUTHENTICATION_CHOICE_SASL_TAG=(byte)0xa3;
    public static final byte AUTHENTICATION_CHOICE_SIMPLE_TAG=(byte)0x80;
    public static final byte REQUEST_TAG=0x60;
    public static final int VERSION_3=3;

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
                VERSION_3);
    }

    @Override
    public @NotNull BindRequest self() {
        return this;
    }

    public static @NotNull BindRequest simple(@NotNull String name, char[] password) {
        return new BindRequest(
                new AuthenticationChoice.Simple(password),
                name,
                VERSION_3);
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.bindRequest(this);
    }

    @Override
    public @NotNull ByteBuffer write() {
        return BER.writeTag(
                REQUEST_TAG,
                BER.writeIntegerTag(version)
                        .append(BER.writeUtf8Tag(name))
                        .append(authentication.write()));
    }
}
