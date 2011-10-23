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
 * An encapsulation of information in Bento about a field.
 *
 * @author mcculley
 */
class Field {

    final String name;
    final String type;
    final String column;

    Field(String name, String type, String column) {
        this.name = name;
        this.type = type;
        this.column = column;
    }

    @Override
    public String toString() {
        return "{" + "name=" + name + ", type=" + type + ", column=" + column + '}';
    }
}
