package hu.gds.ldap4j.net.netty.codec;

import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.SearchResult;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record SearchResponse(
        @NotNull List<@NotNull ControlsMessage<SearchResult>> searchResults)
        implements Response {
    public SearchResponse(@NotNull List<@NotNull ControlsMessage<SearchResult>> searchResults) {
        this.searchResults=Objects.requireNonNull(searchResults, "searchResults");
    }

    @Override
    public <T> T visit(@NotNull Visitor<T> visitor) throws Throwable {
        return visitor.searchResponse(this);
    }
}
