package hu.gds.ldap4j.ldap;

import org.jetbrains.annotations.NotNull;

public class MatchingRules {
    public static final @NotNull String BOOLEAN_MATCH="2.5.13.13";
    public static final @NotNull String CASE_EXACT_MATCH="2.5.13.5";
    public static final @NotNull String CASE_IGNORE_MATCH="2.5.13.2";
    public static final @NotNull String CASE_IGNORE_ORDERING_MATCH="2.5.13.6";
    public static final @NotNull String DISTINGUISHED_NAME_MATCH="2.5.13.1";
    public static final @NotNull String INTEGER_MATCH="2.5.13.14";
    public static final @NotNull String OCTET_STRING_MATCH="2.5.13.17";
    public static final @NotNull String UNIQUE_MEMBER_MATCH="2.5.13.23";
    
    private MatchingRules() {
    }
}
