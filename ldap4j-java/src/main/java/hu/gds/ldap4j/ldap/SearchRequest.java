package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record SearchRequest(
        @NotNull List<@NotNull ByteBuffer> attributes,
        @NotNull ByteBuffer baseObject,
        @NotNull DerefAliases derefAliases,
        @NotNull Filter filter,
        @NotNull Scope scope,
        int sizeLimitEntries,
        int timeLimitSeconds,
        boolean typesOnly)
        implements Message<SearchRequest> {
    public static final @NotNull String ALL_ATTRIBUTES="*";
    public static final @NotNull String NO_ATTRIBUTES="1.1";
    public static final byte REQUEST_TAG=0x63;

    public SearchRequest(
            @NotNull List<@NotNull ByteBuffer> attributes,
            @NotNull ByteBuffer baseObject,
            @NotNull DerefAliases derefAliases,
            @NotNull Filter filter,
            @NotNull Scope scope,
            int sizeLimitEntries,
            int timeLimitSeconds,
            boolean typesOnly) {
        if (0>sizeLimitEntries) {
            throw new IllegalArgumentException("negative sizeLimitEntries %d".formatted(sizeLimitEntries));
        }
        if (0>timeLimitSeconds) {
            throw new IllegalArgumentException("negative timeLimitSeconds %d".formatted(timeLimitSeconds));
        }
        this.attributes=Objects.requireNonNull(attributes, "attributes");
        this.baseObject=Objects.requireNonNull(baseObject, "baseObject");
        this.derefAliases=Objects.requireNonNull(derefAliases, "derefAliases");
        this.filter=Objects.requireNonNull(filter, "filter");
        this.scope=Objects.requireNonNull(scope, "scope");
        this.sizeLimitEntries=sizeLimitEntries;
        this.timeLimitSeconds=timeLimitSeconds;
        this.typesOnly=typesOnly;
    }
    
    public SearchRequest(
            @NotNull List<@NotNull String> attributes,
            @NotNull String baseObject,
            @NotNull DerefAliases derefAliases,
            @NotNull Filter filter,
            @NotNull Scope scope,
            int sizeLimitEntries,
            int timeLimitSeconds,
            boolean typesOnly) {
        this(
                attributes.stream()
                        .map(ByteBuffer::create)
                        .toList(),
                ByteBuffer.create(baseObject),
                derefAliases,
                filter,
                scope,
                sizeLimitEntries,
                timeLimitSeconds,
                typesOnly);
    }

    @Override
    public @NotNull SearchRequest self() {
        return this;
    }

    @Override
    public @NotNull ByteBuffer write() throws Throwable {
        return BER.writeTag(
                REQUEST_TAG,
                BER.writeOctetStringTag(baseObject)
                        .append(scope.write())
                        .append(derefAliases.write())
                        .append(BER.writeIntegerTag(sizeLimitEntries))
                        .append(BER.writeIntegerTag(timeLimitSeconds))
                        .append(BER.writeBooleanTag(typesOnly))
                        .append(filter.write())
                        .append(BER.writeSequence(BER.writeIterable(BER::writeOctetStringTag, attributes))));
    }
}
