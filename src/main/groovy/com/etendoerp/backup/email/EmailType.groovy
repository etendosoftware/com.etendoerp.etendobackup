package com.etendoerp.backup.email

enum EmailType {
    ERROR   (0,'ERROR'),
    WARNING (1,'WARNING'),
    SUCCESS (2,'SUCCESS')

    private final int key
    private final String val

    EmailType(int key, String val) {
        this.key = key
        this.val = val
    }

    String getValue() {
        val
    }
}
