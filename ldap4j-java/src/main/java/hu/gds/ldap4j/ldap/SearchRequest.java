package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public record SearchRequest(
        @NotNull List<@NotNull String> attributes,
        @NotNull String baseObject,
        @NotNull DerefAliases derefAliases,
        @NotNull Filter filter,
        @NotNull Scope scope,
        int sizeLimitEntries,
        boolean sizeTimeLimitSignKludge,
        int timeLimitSeconds,
        boolean typesOnly) {
    public SearchRequest(
            @NotNull List<@NotNull String> attributes, @NotNull String baseObject,
            @NotNull DerefAliases derefAliases, @NotNull Filter filter, @NotNull Scope scope,
            int sizeLimitEntries, boolean sizeTimeLimitSignKludge, int timeLimitSeconds, boolean typesOnly) {
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
        this.sizeTimeLimitSignKludge=sizeTimeLimitSignKludge;
        this.timeLimitSeconds=timeLimitSeconds;
        this.typesOnly=typesOnly;
    }

    public SearchRequest(
            @NotNull List<@NotNull String> attributes, @NotNull String baseObject,
            @NotNull DerefAliases derefAliases, @NotNull Filter filter, @NotNull Scope scope,
            int sizeLimitEntries, int timeLimitSeconds, boolean typesOnly) {
        this(
                attributes, baseObject, derefAliases, filter, scope, sizeLimitEntries,
                true, timeLimitSeconds, typesOnly);
    }

    public ByteBuffer write() throws Throwable {
        return DER.writeTag(
                Ldap.PROTOCOL_OP_SEARCH_REQUEST,
                DER.writeUtf8Tag(baseObject)
                        .append(scope.write())
                        .append(derefAliases.write())
                        .append(DER.writeIntegerTag(sizeTimeLimitSignKludge, sizeLimitEntries))
                        .append(DER.writeIntegerTag(sizeTimeLimitSignKludge, timeLimitSeconds))
                        .append(DER.writeBooleanTag(typesOnly))
                        .append(filter.write())
                        .append(DER.writeSequence(DER.writeIterable(DER::writeUtf8Tag, attributes))));
    }
}
