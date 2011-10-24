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

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.IAxis;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.pointpainters.PointPainterDisc;
import info.monitorenter.gui.chart.rangepolicies.RangePolicyFixedViewport;
import info.monitorenter.gui.chart.traces.Trace2DSimple;
import info.monitorenter.util.Range;
import java.awt.Component;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 * A simple tool for making charts from Bento databases.
 *
 * @author mcculley
 */
public class BentoGrapher {

    // Types of fields we ignore when graphing.
    private static final ImmutableSet<String> ignorableFields = ImmutableSet.of("com.filemaker.bento.field.layout.horizontalSeparator",
            "com.filemaker.bento.field.core.text",
            "com.filemaker.bento.field.layout.textBox",
            "com.filemaker.bento.field.layout.columnDivider",
            "com.filemaker.bento.field.core.media");

    private static Connection openConnection(File file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.getPath());
    }

    private static Collection<Field> getFields(Connection connection, Library library) throws SQLException {
        ImmutableList.Builder<Field> fields = new ImmutableList.Builder();
        PreparedStatement ps = connection.prepareStatement("SELECT gn_label AS label, gn_name AS column, gn_typeName AS type FROM gn_field WHERE gn_domain=?");
        try {
            ps.setInt(1, library.domain);
            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    String name = rs.getString("label");
                    String column = rs.getString("column");
                    String type = rs.getString("type");
                    fields.add(new Field(name, type, "gn_" + column));
                }
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }

        return fields.build();
    }

    private static Object select(Collection<?> libraries, String message, String title) {
        Object[] possibleValues = libraries.toArray();
        Component parent = null;
        int type = JOptionPane.INFORMATION_MESSAGE;
        Icon icon = null;
        Object defaultValue = possibleValues[0];
        Object selectedValue = JOptionPane.showInputDialog(parent, message, title, type, icon, possibleValues, defaultValue);
        return selectedValue;
    }

    private static Map<String, Library> getLibraries(Connection connection) throws SQLException {
        ImmutableMap.Builder<String, Library> libraries = new ImmutableMap.Builder();
        PreparedStatement ps = connection.prepareStatement("SELECT gn_sourceitem.gn_label AS label, gn_domain.gn_name AS name, gn_domain.gnpk AS domain FROM gn_sourceitem INNER JOIN gn_domain ON (gn_sourceitem.gn_domain = gn_domain.gnpk) where gn_sourceitem.gn_parent is null");
        try {
            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String label = rs.getString("label");
                    int domain = rs.getInt("domain");
                    libraries.put(label, new Library(name, label, domain));
                }
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }

        return libraries.build();
    }

    private static Map<Number, Number> getData(Connection connection, Library library, Field x, Field y) throws SQLException {
        Map<Number, Number> data = new TreeMap();
        PreparedStatement ps = connection.prepareStatement(String.format("SELECT %s AS x, %s AS y FROM %s WHERE %s IS NOT NULL", x.column, y.column, library.tableName(), y.column));
        try {
            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    double xValue = rs.getDouble("x");
                    double yValue = rs.getDouble("y");
                    data.put(xValue, yValue);
                }
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }


        return ImmutableMap.copyOf(data);
    }

    private static void makeGraph(Connection connection, Library library, Field x, Field y) throws SQLException {
        Map<Number, Number> data = getData(connection, library, x, y);
        Chart2D chart = new Chart2D();
        ITrace2D trace = new Trace2DSimple();
        trace.setName(y.name);
        trace.setPhysicalUnits(x.name, y.name);
        chart.addTrace(trace);
        trace.setPointHighlighter(new PointPainterDisc());
        for (Map.Entry<Number, Number> entry : data.entrySet()) {
            double xValue = entry.getKey().doubleValue();
            double yValue = entry.getValue().doubleValue();
            trace.addPoint(xValue, yValue);
        }

        // FIXME: This can't be the right way to do this. I just want to set the min Y to 0 instead of the smallest value.
        chart.getAxisY().setRangePolicy(new RangePolicyFixedViewport(new Range(0, chart.getAxisY().getMax())));

        chart.getAxisX().setAxisTitle(new IAxis.AxisTitle(x.name));

        JFrame frame = new JFrame(y.name);
        frame.getContentPane().add(chart);
        frame.setSize(800, 600);
        frame.setVisible(true);
    }

    private static File findDatabase() {
        File home = new File(System.getProperty("user.home"));
        return new File(home, "Library/Application Support/Bento/bento.bentodb/Contents/Resources/Database");
    }

    private static Comparator<Field> makeTypeComparator(final String typeName) {
        return new Comparator<Field>() {

            @Override
            public int compare(Field t, Field t1) {
                if (t.type.equals(t1.type)) {
                    return 0;
                } else {
                    if (t.type.equals(typeName)) {
                        return -1;
                    } else if (t1.type.equals(typeName)) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            }
        };
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        File db = findDatabase();
        Connection connection = openConnection(db);
        try {
            Map<String, Library> libraries = getLibraries(connection);
            String selected = (String) select(libraries.keySet(), "Choose Library", "Library");
            Library library = libraries.get(selected);
            Collection<Field> fields = getFields(connection, library);

            fields = Collections2.filter(fields, new Predicate<Field>() {

                @Override
                public boolean apply(Field t) {
                    return !ignorableFields.contains(t.type);
                }
            });

            Comparator<Field> dateComparator = makeTypeComparator("com.filemaker.bento.field.core.date");
            Comparator<Field> dateCreatedComparator = makeTypeComparator("com.filemaker.bento.field.private.timestamp.dateCreated");
            Comparator<Field> dateModifiedComparator = makeTypeComparator("com.filemaker.bento.field.private.timestamp.dateModified");
            Ordering<Field> xOrdering = Ordering.from(dateComparator).compound(dateCreatedComparator).compound(dateModifiedComparator);
            Collection<Field> sortedXFields = xOrdering.immutableSortedCopy(fields);
            final Field x = (Field) select(sortedXFields, "Choose X", "X");

            fields = Collections2.filter(fields, new Predicate<Field>() {

                @Override
                public boolean apply(Field t) {
                    return t != x;
                }
            });

            Ordering<Field> yOrdering = Ordering.from(Collections.reverseOrder(dateModifiedComparator)).compound(Collections.reverseOrder(dateCreatedComparator)).compound(Collections.reverseOrder(dateComparator));
            Collection<Field> sortedYFields = yOrdering.immutableSortedCopy(fields);
            Field y = (Field) select(sortedYFields, "Choose Y", "Y");
            makeGraph(connection, library, x, y);
        } finally {
            connection.close();
        }
    }
}
