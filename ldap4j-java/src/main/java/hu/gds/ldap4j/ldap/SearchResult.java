package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.net.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public sealed interface SearchResult {
    record Done(
            @NotNull LdapResult ldapResult)
            implements SearchResult {
        public Done(@NotNull LdapResult ldapResult) {
            this.ldapResult=Objects.requireNonNull(ldapResult, "ldapResult");
        }

        @Override
        public @NotNull Done asDone() {
            return this;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        public static @NotNull SearchResult.Done readNoTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
            LdapResult ldapResult=LdapResult.read(reader);
            return new SearchResult.Done(ldapResult);
        }

        @Override
        public <T> T visit(Visitor<T> visitor) throws Throwable {
            return visitor.done(this);
        }
    }

    record Entry(
            @NotNull List<@NotNull PartialAttribute> attributes,
            @NotNull ByteBuffer objectName)
            implements SearchResult {
        public Entry(@NotNull List<@NotNull PartialAttribute> attributes, @NotNull ByteBuffer objectName) {
            this.attributes=Objects.requireNonNull(attributes, "attributes");
            this.objectName=Objects.requireNonNull(objectName, "objectName");
        }

        @Override
        public @NotNull Entry asEntry() {
            return this;
        }

        @Override
        public boolean isEntry() {
            return true;
        }

        public static @NotNull SearchResult.Entry readNoTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
            @NotNull ByteBuffer objectName=BER.readOctetStringTag(reader);
            @NotNull List<@NotNull PartialAttribute> attributes=PartialAttribute.readAttributes(reader);
            return new SearchResult.Entry(attributes, objectName);
        }

        @Override
        public <T> T visit(Visitor<T> visitor) throws Throwable {
            return visitor.entry(this);
        }
    }

    class Reader implements MessageReader<SearchResult> {
        @Override
        public void check(
                @NotNull List<@NotNull Control> controls, @NotNull SearchResult message, int messageId)
                throws Throwable {
            message.visit(new Visitor<Void>() {
                @Override
                public Void done(@NotNull Done done) throws Throwable {
                    done.ldapResult.checkSuccess(controls, messageId);
                    return null;
                }

                @Override
                public Void entry(@NotNull Entry entry) {
                    return null;
                }

                @Override
                public Void referral(@NotNull Referral referral) {
                    return null;
                }
            });
        }

        @Override
        public @NotNull SearchResult read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            return BER.readTag(
                    (tag)->switch (tag) {
                        case RESULT_DONE_TAG -> Either.left(Done::readNoTag);
                        case RESULT_ENTRY_TAG -> Either.left(Entry::readNoTag);
                        case RESULT_REFERRAL_TAG -> Either.left(Referral::readNoTag);
                        default -> throw new RuntimeException(
                                "unexpected tag 0x%x, expected 0x%x, 0x%x, or 0x%x".formatted(
                                        tag,
                                        RESULT_DONE_TAG,
                                        RESULT_ENTRY_TAG,
                                        RESULT_REFERRAL_TAG));
                    },
                    reader);
        }
    }

    record Referral(
            @NotNull List<@NotNull ByteBuffer> uris)
            implements SearchResult {
        public Referral(@NotNull List<@NotNull ByteBuffer> uris) {
            this.uris=Objects.requireNonNull(uris, "uris");
        }

        @Override
        public @NotNull Referral asReferral() {
            return this;
        }

        @Override
        public boolean isReferral() {
            return true;
        }

        public static @NotNull SearchResult.Referral readNoTag(@NotNull ByteBuffer.Reader reader) throws Throwable {
            List<@NotNull ByteBuffer> uris=new ArrayList<>();
            while (reader.hasRemainingBytes()) {
                uris.add(BER.readOctetStringTag(reader));
            }
            return new SearchResult.Referral(uris);
        }

        public @NotNull List<@NotNull String> urisUtf8() {
            return uris()
                    .stream()
                    .map(ByteBuffer::utf8)
                    .toList();
        }

        @Override
        public <T> T visit(Visitor<T> visitor) throws Throwable {
            return visitor.referral(this);
        }
    }

    @NotNull MessageReader<SearchResult> READER=new Reader();
    byte RESULT_DONE_TAG=0x65;
    byte RESULT_ENTRY_TAG=0x64;
    byte RESULT_REFERRAL_TAG=0x73;

    interface Visitor<T> {
        T done(@NotNull Done done) throws Throwable;

        T entry(@NotNull Entry entry) throws Throwable;

        T referral(@NotNull Referral referral) throws Throwable;
    }

    default @NotNull Done asDone() {
        throw new ClassCastException("cannot cast %s to %s".formatted(this, Done.class));
    }

    default @NotNull Entry asEntry() {
        throw new ClassCastException("cannot cast %s to %s".formatted(this, Entry.class));
    }

    default @NotNull Referral asReferral() {
        throw new ClassCastException("cannot cast %s to %s".formatted(this, Referral.class));
    }

    default boolean isDone() {
        return false;
    }

    default boolean isEntry() {
        return false;
    }

    default boolean isReferral() {
        return false;
    }

    <T> T visit(Visitor<T> visitor) throws Throwable;
}
