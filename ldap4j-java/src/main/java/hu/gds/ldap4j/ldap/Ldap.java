package hu.gds.ldap4j.ldap;


public abstract class Ldap {
    public static final String ALL_ATTRIBUTES="*";
    public static final String ALL_OPERATIONAL_ATTRIBUTES="+";
    public static final byte AUTHENTICATION_CHOICE_SASL=(byte)0xa3;
    public static final byte AUTHENTICATION_CHOICE_SIMPLE=(byte)0x80;
    public static final byte BIND_RESPONSE_CREDENTIALS=(byte)0x87;
    public static final String CONTROL_MANAGE_DSA_IT_OID="2.16.840.1.113730.3.4.2";
    public static final String CONTROL_TRANSACTION_SPECIFICATION_OID="1.3.6.1.1.21.2";
    public static final String EXTENDED_REQUEST_CANCEL_OP_OID="1.3.6.1.1.8";
    public static final String EXTENDED_REQUEST_END_TRANSACTION_OID="1.3.6.1.1.21.3";
    public static final String EXTENDED_REQUEST_FAST_BIND_OID="1.2.840.113556.1.4.1781";
    public static final String EXTENDED_REQUEST_PASSWORD_MODIFY="1.3.6.1.4.1.4203.1.11.1";
    public static final String EXTENDED_REQUEST_START_TLS_OID="1.3.6.1.4.1.1466.20037";
    public static final String EXTENDED_REQUEST_START_TRANSACTION_OID="1.3.6.1.1.21.1";
    public static final String EXTENDED_REQUEST_WHO_AM_I="1.3.6.1.4.1.4203.1.11.3";
    public static final byte FILTER_AND=(byte)0xa0;
    public static final byte FILTER_APPROX_MATCH=(byte)0xa8;
    public static final byte FILTER_EQUALITY_MATCH=(byte)0xa3;
    public static final byte FILTER_EXTENSIBLE_MATCH=(byte)0xa9;
    public static final byte FILTER_EXTENSIBLE_MATCH_DN_ATTRIBUTES=(byte)0x84;
    public static final byte FILTER_EXTENSIBLE_MATCH_MATCH_VALUE=(byte)0x83;
    public static final byte FILTER_EXTENSIBLE_MATCH_MATCHING_RULE=(byte)0x81;
    public static final byte FILTER_EXTENSIBLE_MATCH_TYPE=(byte)0x82;
    public static final byte FILTER_GREATER_OR_EQUAL=(byte)0xa5;
    public static final byte FILTER_LESS_OR_EQUAL=(byte)0xa6;
    public static final byte FILTER_NOT=(byte)0xa2;
    public static final byte FILTER_OR=(byte)0xa1;
    public static final byte FILTER_PRESENT=(byte)0x87;
    public static final byte FILTER_SUBSTRINGS=(byte)0xa4;
    public static final byte FILTER_SUBSTRINGS_ANY=(byte)0x81;
    public static final byte FILTER_SUBSTRINGS_FINAL=(byte)0x82;
    public static final byte FILTER_SUBSTRINGS_INITIAL=(byte)0x80;
    public static final byte LDAP_RESULT_REFERRALS=(byte)0xa3;
    public static final byte MESSAGE_CONTROLS=(byte)0xa0;
    public static final byte MODIFY_DN_REQUEST_NEW_SUPERIOR=(byte)0x80;
    public static final String NO_ATTRIBUTES="1.1";
    public static final String NOTICE_OF_DISCONNECTION_OID="1.3.6.1.4.1.1466.20036";
    public static final byte PROTOCOL_OP_ABANDON_REQUEST=0x70;
    public static final byte PROTOCOL_OP_ADD_REQUEST=0x68;
    public static final byte PROTOCOL_OP_ADD_RESPONSE=0x69;
    public static final byte PROTOCOL_OP_BIND_REQUEST=0x60;
    public static final byte PROTOCOL_OP_BIND_RESPONSE=0x61;
    public static final byte PROTOCOL_OP_COMPARE_REQUEST=0x6e;
    public static final byte PROTOCOL_OP_COMPARE_RESPONSE=0x6f;
    public static final byte PROTOCOL_OP_DELETE_REQUEST=0x4a;
    public static final byte PROTOCOL_OP_DELETE_RESPONSE=0x6b;
    public static final byte PROTOCOL_OP_EXTENDED_REQUEST=0x77;
    public static final byte PROTOCOL_OP_EXTENDED_REQUEST_NAME=(byte)0x80;
    public static final byte PROTOCOL_OP_EXTENDED_REQUEST_VALUE=(byte)0x81;
    public static final byte PROTOCOL_OP_EXTENDED_RESPONSE=0x78;
    public static final byte PROTOCOL_OP_EXTENDED_RESPONSE_NAME=(byte)0x8a;
    public static final byte PROTOCOL_OP_EXTENDED_RESPONSE_VALUE=(byte)0x8b;
    public static final byte PROTOCOL_OP_MODIFY_DN_REQUEST=0x6c;
    public static final byte PROTOCOL_OP_MODIFY_DN_RESPONSE=0x6d;
    public static final byte PROTOCOL_OP_MODIFY_REQUEST=0x66;
    public static final byte PROTOCOL_OP_MODIFY_RESPONSE=0x67;
    public static final byte PROTOCOL_OP_SEARCH_REQUEST=0x63;
    public static final byte PROTOCOL_OP_SEARCH_RESULT_DONE=0x65;
    public static final byte PROTOCOL_OP_SEARCH_RESULT_ENTRY=0x64;
    public static final byte PROTOCOL_OP_SEARCH_RESULT_REFERRAL=0x73;
    public static final byte PROTOCOL_OP_UNBIND_REQUEST=0x42;
    public static final int VERSION=3;

    private Ldap() {
    }
}
