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

public abstract class Filter {
    public static class And extends ListFilter {
        public And(List<Filter> filters) {
            super(filters);
        }

        @Override
        protected byte tag() {
            return AND_TAG;
        }

        @Override
        protected @NotNull String toStringRelation() {
            return "&";
        }
    }

    public static class ApproxMatch extends AttributeValueAssertion {
        public static final String RELATION_STRING="~";

        public ApproxMatch(@NotNull ByteBuffer assertionValue, @NotNull ByteBuffer attributeDescription) {
            super(assertionValue, attributeDescription);
        }

        @Override
        protected byte tag() {
            return APPROX_MATCH_TAG;
        }

        @Override
        protected @NotNull String toStringRelation() {
            return RELATION_STRING;
        }
    }

    public static class Or extends ListFilter {
        public Or(List<Filter> filters) {
            super(filters);
        }

        @Override
        protected byte tag() {
            return OR_TAG;
        }

        @Override
        protected @NotNull String toStringRelation() {
            return "|";
        }
    }

    public static abstract class AttributeValueAssertion extends Filter {
        public final @NotNull ByteBuffer assertionValue;
        public final @NotNull ByteBuffer attributeDescription;

        public AttributeValueAssertion(
                @NotNull ByteBuffer assertionValue, @NotNull ByteBuffer attributeDescription) {
            this.assertionValue=Objects.requireNonNull(assertionValue, "assertionValue");
            this.attributeDescription=Objects.requireNonNull(attributeDescription, "attributeDescription");
        }

        @Override
        public String toString() {
            return "("+attributeDescription+toStringRelation()+assertionValue+")";
        }

        protected abstract @NotNull String toStringRelation();

        @Override
        protected @NotNull ByteBuffer writeContent() {
            return BER.writeOctetStringTag(attributeDescription)
                    .append(BER.writeOctetStringTag(assertionValue));
        }
    }

    public static class EqualityMatch extends AttributeValueAssertion {
        public static final String RELATION_STRING="=";

        public EqualityMatch(@NotNull ByteBuffer assertionValue, @NotNull ByteBuffer attributeDescription) {
            super(assertionValue, attributeDescription);
        }

        @Override
        protected byte tag() {
            return EQUALITY_MATCH_TAG;
        }

        @Override
        protected @NotNull String toStringRelation() {
            return RELATION_STRING;
        }
    }

    public static class ExtensibleMatch extends Filter {
        public final boolean dnAttributes;
        public final @Nullable ByteBuffer matchingRule;
        public final @NotNull ByteBuffer matchValue;
        public final @Nullable ByteBuffer type;

        public ExtensibleMatch(
                boolean dnAttributes,
                @Nullable ByteBuffer matchingRule,
                @NotNull ByteBuffer matchValue,
                @Nullable ByteBuffer type) {
            this.dnAttributes=dnAttributes;
            this.matchingRule=matchingRule;
            this.matchValue=Objects.requireNonNull(matchValue, "matchValue");
            this.type=type;
        }

        @Override
        protected byte tag() {
            return EXTENSIBLE_MATCH_TAG;
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
        protected @NotNull ByteBuffer writeContent() {
            ByteBuffer result=ByteBuffer.empty();
            if (null!=matchingRule) {
                result=result.append(BER.writeTag(
                        EXTENSIBLE_MATCH_MATCHING_RULE_TAG,
                        BER.writeOctetStringNoTag(matchingRule)));
            }
            if (null!=type) {
                result=result.append(BER.writeTag(
                        EXTENSIBLE_MATCH_TYPE_TAG,
                        BER.writeOctetStringNoTag(type)));
            }
            result=result.append(BER.writeTag(
                    EXTENSIBLE_MATCH_MATCH_VALUE_TAG,
                    BER.writeOctetStringNoTag(matchValue)));
            if (dnAttributes) {
                result=result.append(BER.writeTag(
                        EXTENSIBLE_MATCH_DN_ATTRIBUTES_TAG,
                        BER.writeBooleanNoTag(true)));
            }
            return result;
        }
    }

    public static class GreaterOrEqual extends AttributeValueAssertion {
        public static final String RELATION_STRING=">=";

        public GreaterOrEqual(@NotNull ByteBuffer assertionValue, @NotNull ByteBuffer attributeDescription) {
            super(assertionValue, attributeDescription);
        }

        @Override
        protected byte tag() {
            return GREATER_OR_EQUAL_TAG;
        }

        @Override
        protected @NotNull String toStringRelation() {
            return RELATION_STRING;
        }
    }

    public static class LessOrEqual extends AttributeValueAssertion {
        public static final String RELATION_STRING="<=";

        public LessOrEqual(@NotNull ByteBuffer assertionValue, @NotNull ByteBuffer attributeDescription) {
            super(assertionValue, attributeDescription);
        }

        @Override
        protected byte tag() {
            return LESS_OR_EQUAL_TAG;
        }

        @Override
        protected @NotNull String toStringRelation() {
            return RELATION_STRING;
        }
    }

    public static abstract class ListFilter extends Filter {
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

        protected abstract @NotNull String toStringRelation();

        @Override
        protected @NotNull ByteBuffer writeContent() throws Throwable {
            ByteBuffer result=ByteBuffer.empty();
            for (Filter filter : filters) {
                result=result.append(filter.write());
            }
            return result;
        }
    }

    public static class Not extends Filter {
        public final @NotNull Filter filter;

        public Not(@NotNull Filter filter) {
            this.filter=Objects.requireNonNull(filter, "filter");
        }

        @Override
        protected byte tag() {
            return NOT_TAG;
        }

        @Override
        public String toString() {
            return "(!"+filter+")";
        }

        @Override
        protected @NotNull ByteBuffer writeContent() throws Throwable {
            return filter.write();
        }
    }

    public static class Present extends Filter {
        public final @NotNull ByteBuffer attribute;

        public Present(@NotNull ByteBuffer attribute) {
            this.attribute=Objects.requireNonNull(attribute, "attribute");
        }

        @Override
        public String toString() {
            return "("+attribute+"=*)";
        }

        @Override
        protected byte tag() {
            return PRESENT_TAG;
        }

        @Override
        protected @NotNull ByteBuffer writeContent() {
            return BER.writeOctetStringNoTag(attribute);
        }
    }

    public static class Substrings extends Filter {
        public final @Nullable List<@NotNull ByteBuffer> any;
        public final @Nullable ByteBuffer final2;
        public final @Nullable ByteBuffer initial;
        public final @NotNull ByteBuffer type;

        public Substrings(
                @Nullable List<@NotNull ByteBuffer> any,
                @Nullable ByteBuffer final2,
                @Nullable ByteBuffer initial,
                @NotNull ByteBuffer type) {
            this.any=any;
            this.final2=final2;
            this.initial=initial;
            this.type=Objects.requireNonNull(type, "type");
        }

        @Override
        protected byte tag() {
            return SUBSTRINGS_TAG;
        }

        @Override
        public String toString() {
            String final3=(null==final2) ? "" : final2.toString();
            String initial3=(null==initial) ? "" : initial.toString();
            if (null==any) {
                return "(%s=%s*%s)".formatted(type, initial3, final3);
            }
            else {
                return "(%s=%s*%s*%s)".formatted(
                        type,
                        initial3,
                        String.join(
                                "*",
                                any.stream()
                                        .map(Object::toString)
                                        .toList()),
                        final3);
            }
        }

        @Override
        protected @NotNull ByteBuffer writeContent() {
            ByteBuffer substrings=ByteBuffer.empty();
            if (null!=initial) {
                substrings=substrings.append(
                        BER.writeTag(SUBSTRINGS_INITIAL_TAG, BER.writeOctetStringNoTag(initial)));
            }
            if (null!=any) {
                for (@NotNull ByteBuffer any2: any) {
                    substrings=substrings.append(
                            BER.writeTag(SUBSTRINGS_ANY_TAG, BER.writeOctetStringNoTag(any2)));
                }
            }
            if (null!=final2) {
                substrings=substrings.append(
                        BER.writeTag(SUBSTRINGS_FINAL_TAG, BER.writeOctetStringNoTag(final2)));
            }
            return BER.writeOctetStringTag(type)
                    .append(BER.writeSequence(substrings));
        }
    }
    
    public static final byte AND_TAG=(byte)0xa0;
    public static final byte APPROX_MATCH_TAG=(byte)0xa8;
    public static final byte EQUALITY_MATCH_TAG=(byte)0xa3;
    public static final byte EXTENSIBLE_MATCH_TAG=(byte)0xa9;
    public static final byte EXTENSIBLE_MATCH_DN_ATTRIBUTES_TAG=(byte)0x84;
    public static final byte EXTENSIBLE_MATCH_MATCH_VALUE_TAG=(byte)0x83;
    public static final byte EXTENSIBLE_MATCH_MATCHING_RULE_TAG=(byte)0x81;
    public static final byte EXTENSIBLE_MATCH_TYPE_TAG=(byte)0x82;
    public static final byte GREATER_OR_EQUAL_TAG=(byte)0xa5;
    public static final byte LESS_OR_EQUAL_TAG=(byte)0xa6;
    public static final byte NOT_TAG=(byte)0xa2;
    public static final byte OR_TAG=(byte)0xa1;
    public static final byte PRESENT_TAG=(byte)0x87;
    public static final byte SUBSTRINGS_TAG=(byte)0xa4;
    public static final byte SUBSTRINGS_ANY_TAG=(byte)0x81;
    public static final byte SUBSTRINGS_FINAL_TAG=(byte)0x82;
    public static final byte SUBSTRINGS_INITIAL_TAG=(byte)0x80;

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
                    @NotNull ByteBuffer right=ByteBuffer.create(string2.substring(index+1).trim());
                    Filter filter=switch (dd) {
                        case ':' -> {
                            boolean dnAttributes=false;
                            ByteBuffer matchingRule=null;
                            ByteBuffer type=null;
                            int index2=left.lastIndexOf(':');
                            if (0<=index2) {
                                String part=left.substring(index2+1);
                                left=left.substring(0, index2);
                                if ("dn".equalsIgnoreCase(part)) {
                                    dnAttributes=true;
                                }
                                else {
                                    matchingRule=ByteBuffer.create(part);
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
                                type=ByteBuffer.create(left);
                            }
                            yield new ExtensibleMatch(dnAttributes, matchingRule, right, type);
                        }
                        case '>' -> new GreaterOrEqual(right, ByteBuffer.create(left));
                        case '<' -> new LessOrEqual(right, ByteBuffer.create(left));
                        case '~' -> new ApproxMatch(right, ByteBuffer.create(left));
                        default -> null;
                    };
                    if (null!=filter) {
                        yield filter;
                    }
                }
                @NotNull ByteBuffer left=ByteBuffer.create(string2.substring(0, index).trim());
                String right=string2.substring(index+1).trim();
                if ("*".equals(right)) {
                    yield new Present(left);
                }
                int index2=right.indexOf('*');
                if (0>index2) {
                    yield new EqualityMatch(ByteBuffer.create(right), left);
                }
                ByteBuffer initial=null;
                if (0<index2) {
                    initial=ByteBuffer.create(right.substring(0, index2));
                }
                right=right.substring(index2+1);
                ByteBuffer final2=null;
                index2=right.lastIndexOf('*');
                if (0>index2) {
                    final2=ByteBuffer.create(right);
                    right="";
                }
                else {
                    if (right.length()>index2+1) {
                        final2=ByteBuffer.create(right.substring(index2+1));
                    }
                    right=right.substring(0, index2);
                }
                List<@NotNull ByteBuffer> any=null;
                if (!right.isEmpty()) {
                    any=new ArrayList<>();
                    while (!right.isEmpty()) {
                        int index3=right.indexOf('*');
                        if (0>index3) {
                            any.add(ByteBuffer.create(right));
                            right="";
                        }
                        else {
                            any.add(ByteBuffer.create(right.substring(0, index3)));
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

    public @NotNull ByteBuffer write() throws Throwable {
        return BER.writeTag(tag(), writeContent());
    }

    protected abstract @NotNull ByteBuffer writeContent() throws Throwable;
}
