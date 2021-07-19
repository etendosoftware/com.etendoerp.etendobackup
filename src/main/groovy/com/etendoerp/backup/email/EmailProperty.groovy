package com.etendoerp.backup.email

enum EmailProperty {

    TO      ("TO"),
    CC      ("CC"),
    SUBJECT ("SUBJECT"),

    private final String val

    EmailProperty(String value) {
        this.val = value
    }

    String getValue() {
        val
    }

    static boolean containsVal(String val) {
        values()*.val.contains(val)
    }

}
