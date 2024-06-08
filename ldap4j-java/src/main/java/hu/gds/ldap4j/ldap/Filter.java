package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.net.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public sealed abstract class Filter {
    public static final class And extends ListFilter {
        public And(List<Filter> filters) {
            super(filters);
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_AND;
        }

        @Override
        protected String toStringRelation() {
            return "&";
        }
    }

    public static final class ApproxMatch extends AttributeValueAssertion {
        public ApproxMatch(@NotNull String assertionValue, @NotNull String attributeDescription) {
            super(assertionValue, attributeDescription);
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_APPROX_MATCH;
        }

        @Override
        protected String toStringRelation() {
            return "~";
        }
    }

    public static final class Or extends ListFilter {
        public Or(List<Filter> filters) {
            super(filters);
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_OR;
        }

        @Override
        protected String toStringRelation() {
            return "|";
        }
    }

    public static sealed abstract class AttributeValueAssertion extends Filter {
        public final @NotNull String assertionValue;
        public final @NotNull String attributeDescription;

        public AttributeValueAssertion(
                @NotNull String assertionValue, @NotNull String attributeDescription) {
            this.assertionValue=Objects.requireNonNull(assertionValue, "assertionValue");
            this.attributeDescription=Objects.requireNonNull(attributeDescription, "attributeDescription");
        }

        @Override
        public String toString() {
            return "("+attributeDescription+toStringRelation()+assertionValue+")";
        }

        protected abstract String toStringRelation();

        @Override
        protected ByteBuffer writeContent() throws Throwable {
            return DER.writeUtf8Tag(attributeDescription)
                    .append(DER.writeUtf8Tag(assertionValue));
        }
    }

    public static final class EqualityMatch extends AttributeValueAssertion {
        public EqualityMatch(@NotNull String assertionValue, @NotNull String attributeDescription) {
            super(assertionValue, attributeDescription);
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_EQUALITY_MATCH;
        }

        @Override
        protected String toStringRelation() {
            return "=";
        }
    }

    public static final class ExtensibleMatch extends Filter {
        public final boolean dnAttributes;
        public final @Nullable String matchingRule;
        public final @NotNull String matchValue;
        public final @Nullable String type;

        public ExtensibleMatch(
                boolean dnAttributes, @Nullable String matchingRule,
                @NotNull String matchValue, @Nullable String type) {
            this.dnAttributes=dnAttributes;
            this.matchingRule=matchingRule;
            this.matchValue=Objects.requireNonNull(matchValue, "matchValue");
            this.type=type;
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_EXTENSIBLE_MATCH;
        }

        @Override
        public String toString() {
            StringBuilder sb=new StringBuilder();
            sb.append('(');
            if (null!=type) {
                sb.append(type);
            }
            if (dnAttributes) {
                sb.append(":dn");
            }
            if (null!=matchingRule) {
                sb.append(':');
                sb.append(matchingRule);
            }
            sb.append(":=");
            sb.append(matchValue);
            sb.append(')');
            return sb.toString();
        }

        @Override
        protected ByteBuffer writeContent() throws Throwable {
            ByteBuffer result=ByteBuffer.EMPTY;
            if (null!=matchingRule) {
                result=result.append(DER.writeTag(
                        Ldap.FILTER_EXTENSIBLE_MATCH_MATCHING_RULE,
                        DER.writeUtf8NoTag(matchingRule)));
            }
            if (null!=type) {
                result=result.append(DER.writeTag(
                        Ldap.FILTER_EXTENSIBLE_MATCH_TYPE,
                        DER.writeUtf8NoTag(type)));
            }
            result=result.append(DER.writeTag(
                    Ldap.FILTER_EXTENSIBLE_MATCH_MATCH_VALUE,
                    DER.writeUtf8NoTag(matchValue)));
            if (dnAttributes) {
                result=result.append(DER.writeTag(
                        Ldap.FILTER_EXTENSIBLE_MATCH_DN_ATTRIBUTES,
                        DER.writeBooleanNoTag(true)));
            }
            return result;
        }
    }

    public static final class GreaterOrEqual extends AttributeValueAssertion {
        public GreaterOrEqual(@NotNull String assertionValue, @NotNull String attributeDescription) {
            super(assertionValue, attributeDescription);
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_GREATER_OR_EQUAL;
        }

        @Override
        protected String toStringRelation() {
            return ">=";
        }
    }

    public static final class LessOrEqual extends AttributeValueAssertion {
        public LessOrEqual(@NotNull String assertionValue, @NotNull String attributeDescription) {
            super(assertionValue, attributeDescription);
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_LESS_OR_EQUAL;
        }

        @Override
        protected String toStringRelation() {
            return "<=";
        }
    }

    private static sealed abstract class ListFilter extends Filter {
        public final @NotNull List<@NotNull Filter> filters;

        public ListFilter(List<Filter> filters) {
            this.filters=Objects.requireNonNull(filters, "filters");
        }

        @Override
        public String toString() {
            StringBuilder sb=new StringBuilder();
            sb.append("(");
            sb.append(toStringRelation());
            for (Filter filter : filters) {
                sb.append(filter);
            }
            sb.append(")");
            return sb.toString();
        }

        protected abstract String toStringRelation();

        @Override
        protected ByteBuffer writeContent() throws Throwable {
            ByteBuffer result=ByteBuffer.EMPTY;
            for (Filter filter : filters) {
                result=result.append(filter.write());
            }
            return result;
        }
    }

    public static final class Not extends Filter {
        public final @NotNull Filter filter;

        public Not(@NotNull Filter filter) {
            this.filter=Objects.requireNonNull(filter, "filter");
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_NOT;
        }

        @Override
        public String toString() {
            return "(!"+filter+")";
        }

        @Override
        protected ByteBuffer writeContent() throws Throwable {
            return filter.write();
        }
    }

    public static final class Present extends Filter {
        public final @NotNull String attribute;

        public Present(@NotNull String attribute) {
            this.attribute=Objects.requireNonNull(attribute, "attribute");
        }

        @Override
        public String toString() {
            return "("+attribute+"=*)";
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_PRESENT;
        }

        @Override
        protected ByteBuffer writeContent() {
            return DER.writeUtf8NoTag(attribute);
        }
    }

    public static final class Substrings extends Filter {
        public final @Nullable List<@NotNull String> any;
        public final @Nullable String final2;
        public final @Nullable String initial;
        public final @NotNull String type;

        public Substrings(
                @Nullable List<@NotNull String> any, @Nullable String final2,
                @Nullable String initial, @NotNull String type) {
            this.any=any;
            this.final2=final2;
            this.initial=initial;
            this.type=Objects.requireNonNull(type, "type");
        }

        @Override
        protected byte tag() {
            return Ldap.FILTER_SUBSTRINGS;
        }

        @Override
        public String toString() {
            String final3=(null==final2) ? "" : final2;
            String initial3=(null==initial) ? "" : initial;
            if (null==any) {
                return "(%s=%s*%s)".formatted(type, initial3, final3);
            }
            else {
                return "(%s=%s*%s*%s)".formatted(type, initial3, String.join("*", any), final3);
            }
        }

        @Override
        protected ByteBuffer writeContent() throws Throwable {
            ByteBuffer substrings=ByteBuffer.EMPTY;
            if (null!=initial) {
                substrings=substrings.append(
                        DER.writeTag(Ldap.FILTER_SUBSTRINGS_INITIAL, DER.writeUtf8NoTag(initial)));
            }
            if (null!=any) {
                for (String any2 : any) {
                    substrings=substrings.append(
                            DER.writeTag(Ldap.FILTER_SUBSTRINGS_ANY, DER.writeUtf8NoTag(any2)));
                }
            }
            if (null!=final2) {
                substrings=substrings.append(
                        DER.writeTag(Ldap.FILTER_SUBSTRINGS_FINAL, DER.writeUtf8NoTag(final2)));
            }
            return DER.writeUtf8Tag(type)
                    .append(DER.writeSequence(substrings));
        }
    }

    public static @NotNull Filter parse(@NotNull String string) throws Throwable {
        try (Reader reader=new StringReader(string)) {
            char cc=parseNonWhitespace(reader, string);
            if ('('!=cc) {
                throw new RuntimeException("invalid filter string %s, missing starting (".formatted(string));
            }
            Filter filter=parseFilter(reader, string);
            while (true) {
                int ii=reader.read();
                if (0>ii) {
                    break;
                }
                char dd=(char) ii;
                if (Character.isWhitespace(dd)) {
                    continue;
                }
                throw new RuntimeException("invalid filter string %s, junk after first top-level )".formatted(string));
            }
            return filter;
        }
    }

    private static char parseChar(@NotNull Reader reader, @NotNull String string) throws Throwable {
        int ii=reader.read();
        if (0>ii) {
            throw new EOFException(string);
        }
        return (char) ii;
    }

    private static @NotNull Filter parseFilter(@NotNull Reader reader, @NotNull String string) throws Throwable {
        char firstChar=parseNonWhitespace(reader, string);
        return switch (firstChar) {
            case '&' -> new And(parseList(reader, string));
            case '(' -> {
                List<Filter> filters=new ArrayList<>();
                filters.add(parseFilter(reader, string));
                filters.addAll(parseList(reader, string));
                yield new And(filters);
            }
            case '!' -> {
                char cc=parseNonWhitespace(reader, string);
                if ('('!=cc) {
                    throw new RuntimeException("invalid filter string %s, missing ( in not".formatted(string));
                }
                Filter filter=parseFilter(reader, string);
                if (!parseList(reader, string).isEmpty()) {
                    throw new RuntimeException(
                            "invalid filter string %s, more than one filter after not".formatted(string));
                }
                yield new Not(filter);
            }
            case '|' -> new Or(parseList(reader, string));
            default -> {
                StringBuilder sb=new StringBuilder();
                sb.append(firstChar);
                char cc=parseChar(reader, string);
                while (')'!=cc) {
                    sb.append(cc);
                    cc=parseChar(reader, string);
                }
                String string2=sb.toString().trim();
                int index=string2.indexOf('=');
                if (0>index) {
                    throw new RuntimeException(
                            "invalid filter string %s, invalid leaf %s".formatted(string, string2));
                }
                if (0<index) {
                    char dd=string2.charAt(index-1);
                    String left=string2.substring(0, index-1).trim();
                    String right=string2.substring(index+1).trim();
                    Filter filter=switch (dd) {
                        case ':' -> {
                            boolean dnAttributes=false;
                            String matchingRule=null;
                            String type=null;
                            int index2=left.lastIndexOf(':');
                            if (0<=index2) {
                                String part=left.substring(index2+1);
                                left=left.substring(0, index2);
                                if ("dn".equalsIgnoreCase(part)) {
                                    dnAttributes=true;
                                }
                                else {
                                    matchingRule=part;
                                }
                            }
                            if (!dnAttributes) {
                                int index3=left.lastIndexOf(':');
                                if (0<=index3) {
                                    String part=left.substring(index3+1);
                                    left=left.substring(0, index3);
                                    if ("dn".equalsIgnoreCase(part)) {
                                        dnAttributes=true;
                                    }
                                    else {
                                        throw new RuntimeException(
                                                "multiple matching rules %s and %s".formatted(matchingRule, part));
                                    }
                                }
                            }
                            if (!left.isEmpty()) {
                                type=left;
                            }
                            yield new ExtensibleMatch(dnAttributes, matchingRule, right, type);
                        }
                        case '>' -> new GreaterOrEqual(right, left);
                        case '<' -> new LessOrEqual(right, left);
                        case '~' -> new ApproxMatch(right, left);
                        default -> null;
                    };
                    if (null!=filter) {
                        yield filter;
                    }
                }
                String left=string2.substring(0, index).trim();
                String right=string2.substring(index+1).trim();
                if ("*".equals(right)) {
                    yield new Present(left);
                }
                int index2=right.indexOf('*');
                if (0>index2) {
                    yield new EqualityMatch(right, left);
                }
                String initial=null;
                if (0<index2) {
                    initial=right.substring(0, index2);
                }
                right=right.substring(index2+1);
                String final2=null;
                index2=right.lastIndexOf('*');
                if (0>index2) {
                    final2=right;
                    right="";
                }
                else {
                    if (right.length()>index2+1) {
                        final2=right.substring(index2+1);
                    }
                    right=right.substring(0, index2);
                }
                List<@NotNull String> any=null;
                if (!right.isEmpty()) {
                    any=new ArrayList<>();
                    while (!right.isEmpty()) {
                        int index3=right.indexOf('*');
                        if (0>index3) {
                            any.add(right);
                            right="";
                        }
                        else {
                            any.add(right.substring(0, index3));
                            right=right.substring(index3+1);
                        }
                    }
                }
                yield new Substrings(any, final2, initial, left);
            }
        };
    }

    private static List<Filter> parseList(@NotNull Reader reader, @NotNull String string) throws Throwable {
        List<Filter> result=new ArrayList<>();
        while (true) {
            char cc=parseNonWhitespace(reader, string);
            if (')'==cc) {
                break;
            }
            if ('('!=cc) {
                throw new RuntimeException(
                        "invalid filter string %s, missing child (".formatted(string));
            }
            result.add(parseFilter(reader, string));
        }
        return result;
    }

    private static char parseNonWhitespace(@NotNull Reader reader, @NotNull String string) throws Throwable {
        char cc=parseChar(reader, string);
        while (Character.isWhitespace(cc)) {
            cc=parseChar(reader, string);
        }
        return cc;
    }

    protected abstract byte tag();

    @Override
    public abstract String toString();

    public ByteBuffer write() throws Throwable {
        return DER.writeTag(tag(), writeContent());
    }

    protected abstract ByteBuffer writeContent() throws Throwable;
}
