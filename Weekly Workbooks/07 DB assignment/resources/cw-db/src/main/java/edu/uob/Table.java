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
        this.nextId = 1;
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

    // 删除一行
    public boolean deleteRowById(String id){
        for(int i = 0; i < rows.size(); ++i) {
            List<String> row = rows.get(i);
            if(row.get(0).equals(id)){
                rows.remove(i);
                return true;
            }
        }
        return false;
    }

    public boolean updateRowById(String id, List<String> newRowData){
        if (newRowData.size() != columns.size() - 1){
            throw new IllegalArgumentException("value number is valid.");
        }
        for(List<String> row: rows){
            if(row.get(0).equals(id)){
                for(int j = 0; j < newRowData.size(); ++j){
                    row.set(j + 1, newRowData.get(j));
                }
                return true;
            }
        }
        return false;
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
