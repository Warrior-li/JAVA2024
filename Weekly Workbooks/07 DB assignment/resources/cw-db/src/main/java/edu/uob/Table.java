package edu.uob;

import java.util.ArrayList;
import java.util.List;

public class Table {

    private String name;

    private List<String> columns;

    private List<List<String>> rows;

    private int nextId;

    public Table(String name, List<String> columns) {
        this.name = name;
        this.columns = new ArrayList<>();
        this.columns.add("id");
        if (columns != null) {
            this.columns.addAll(columns);
        }
        this.rows = new ArrayList<>();
    }

    // 添加一行数据到这个表中
    public void addRow(List<String> rowData) {
        if (rowData.size() != this.columns.size() - 1) {
            throw new IllegalArgumentException("The number of data rows and columns does not match");
        }
        List<String> newRow = new ArrayList<>();
        newRow.add(String.valueOf(nextId));
        newRow.addAll(rowData);
        rows.add(newRow);
        ++nextId;
    }

    public String getName() {
        return name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    
}
