package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        public void check() throws LdapException {
            ldapResult.check();
        }

        @Override
        public boolean isDone() {
            return true;
        }

        public static @NotNull SearchResult.Done read(@NotNull ByteBuffer.Reader reader) throws Throwable {
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
            @NotNull String objectName)
            implements SearchResult {
        public Entry(@NotNull List<@NotNull PartialAttribute> attributes, @NotNull String objectName) {
            this.attributes=Objects.requireNonNull(attributes, "attributes");
            this.objectName=Objects.requireNonNull(objectName, "objectName");
        }

        @Override
        public @NotNull Entry asEntry() {
            return this;
        }

        @Override
        public void check() {
        }

        @Override
        public boolean isEntry() {
            return true;
        }

        public static @NotNull SearchResult.Entry read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            String objectName=DER.readUtf8Tag(reader);
            @NotNull List<@NotNull PartialAttribute> attributes=PartialAttribute.readAttributes(reader);
            return new SearchResult.Entry(attributes, objectName);
        }

        @Override
        public <T> T visit(Visitor<T> visitor) throws Throwable {
            return visitor.entry(this);
        }
    }

    record Referral(
            @NotNull List<@NotNull String> uris)
            implements SearchResult {
        public Referral(@NotNull List<@NotNull String> uris) {
            this.uris=Objects.requireNonNull(uris, "uris");
        }

        @Override
        public @NotNull Referral asReferral() {
            return this;
        }

        @Override
        public void check() {
        }

        @Override
        public boolean isReferral() {
            return true;
        }

        public static @NotNull SearchResult.Referral read(@NotNull ByteBuffer.Reader reader) throws Throwable {
            List<@NotNull String> uris=new ArrayList<>();
            while (reader.hasRemainingBytes()) {
                uris.add(DER.readUtf8Tag(reader));
            }
            return new SearchResult.Referral(uris);
        }

        @Override
        public <T> T visit(Visitor<T> visitor) throws Throwable {
            return visitor.referral(this);
        }
    }

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

    void check() throws LdapException;

    default boolean isDone() {
        return false;
    }

    default boolean isEntry() {
        return false;
    }

    default boolean isReferral() {
        return false;
    }

    static @NotNull SearchResult read(@NotNull ByteBuffer.Reader reader) throws Throwable {
        return DER.readTags(
                Map.of(
                        Ldap.PROTOCOL_OP_SEARCH_RESULT_DONE, Done::read,
                        Ldap.PROTOCOL_OP_SEARCH_RESULT_ENTRY, Entry::read,
                        Ldap.PROTOCOL_OP_SEARCH_RESULT_REFERRAL, Referral::read),
                reader);
    }

    <T> T visit(Visitor<T> visitor) throws Throwable;
}
