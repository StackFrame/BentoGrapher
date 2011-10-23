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
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 *
 * @author mcculley
 */
public class BentoGrapher {

    private static Connection openConnection(File file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.getPath());
    }

    private static Map<String, Field> getFields(Connection connection, Library library) throws SQLException {
        Map<String, Field> fields = new HashMap<String, Field>();
        PreparedStatement ps = connection.prepareStatement("SELECT gn_label AS label, gn_name AS column, gn_typeName AS type FROM gn_field WHERE gn_domain=?");
        try {
            ps.setInt(1, library.domain);
            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    String name = rs.getString("label");
                    String column = rs.getString("column");
                    String type = rs.getString("type");
                    fields.put(name, new Field(name, type, "gn_" + column));
                }
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }

        return fields;
    }

    private static String select(Collection<String> libraries, String message, String title) {
        Object[] possibleValues = libraries.toArray();
        Component parent = null;
        int type = JOptionPane.INFORMATION_MESSAGE;
        Icon icon = null;
        Object defaultValue = possibleValues[0];
        Object selectedValue = JOptionPane.showInputDialog(parent, message, title, type, icon, possibleValues, defaultValue);
        return (String) selectedValue;
    }

    private static Map<String, Library> getLibraries(Connection connection) throws SQLException {
        Map<String, Library> libraries = new HashMap<String, Library>();
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

        return libraries;
    }

    private static Map<Number, Number> getData(Connection connection, Library library, Field x, Field y) throws SQLException {
        Map<Number, Number> data = new TreeMap<Number, Number>();
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


        return data;
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

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        File home = new File(System.getProperty("user.home"));
        File db = new File(home, "Library/Application Support/Bento/bento.bentodb/Contents/Resources/Database");
        Connection connection = openConnection(db);
        try {
            Map<String, Library> libraries = getLibraries(connection);
            String selected = select(libraries.keySet(), "Choose Library", "Library");
            Library library = libraries.get(selected);
            Map<String, Field> fields = getFields(connection, library);
            String xName = select(fields.keySet(), "Choose X", "X");
            Field x = fields.get(xName);
            String yName = select(fields.keySet(), "Choose Y", "Y");
            Field y = fields.get(yName);
            makeGraph(connection, library, x, y);
        } finally {
            connection.close();
        }
    }
}