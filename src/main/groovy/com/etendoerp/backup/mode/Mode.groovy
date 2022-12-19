package com.etendoerp.backup.mode

enum Mode {
    MANUAL('manual'),
    AUTO('auto')

    private final String val

    Mode(String val) {
        this.val = val
    }

    String getValue() {
        val
    }

    static boolean containsVal(String val) {
        values()*.val.contains(val)
    }

    String toString() { val }
}
