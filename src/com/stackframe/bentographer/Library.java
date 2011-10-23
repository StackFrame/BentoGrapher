/*
 * Copyright 2011 StackFrame, LLC
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * You should have received a copy of the GNU General Public License
 * along with this file.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.stackframe.bentographer;

/**
 * An encapsulation of information in Bento about a library.
 *
 * @author mcculley
 */
class Library {

    final String name;
    final String label;
    final int domain;

    Library(String name, String label, int domain) {
        this.name = name;
        this.label = label;
        this.domain = domain;
    }

    String tableName() {
        return "gn_" + name;
    }
}
