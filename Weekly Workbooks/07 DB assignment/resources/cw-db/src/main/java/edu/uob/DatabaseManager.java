package edu.uob;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private String storageFolderPath;

    public DatabaseManager(String storageFolderPath) {
        this.storageFolderPath = storageFolderPath;
    }

    public Map<String, Table> loadAllTables() throws IOException{
        Map<String, Table> tables = new HashMap<>();
        File folder = new File(storageFolderPath);
        File[] files = folder.listFiles();
        if(files == null){
            return tables;
        }
        for(File file: files){
            if(file.isFile()){
                String fileName = file.getName();
                String tableName = fileName.endsWith(".tab") 
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
                Table table = loadTableFromFile(file, tableName);
                tables.put(tableName,table);
            }
        }
        return tables;
    }

    /**
     * 从一个文件中加载数据
     * 加载文件的时候忽略id，因为id是由我们定制，不是由用户定制
     */
    private Table loadTableFromFile(File file, String tableName) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        if(lines.isEmpty()){
            throw new IOException("Table File " + file.getName() + " is empty");
        }
        // 表头 列名
        String headerLine = lines.get(0);
        String[] headers = headerLine.split("\t");
        if(headers.length == 0 || !headers[0].equals("id")){
            throw new IOException("Table File " + file.getName() + "'s first line must be 'id'");
        }

        // id之后都是数据
        List<String> columns = new ArrayList<>();
        for(int i = 0; i < headers.length - 1; ++i){
            columns.add(headers[i+1]);
        }

        Table table = new Table(tableName, columns);

        for(int i = 1; i < lines.size(); ++i){
            String line = lines.get(i);
            // 使用制表符分割
            String[] fileds = line.split("\t", -1);
            if(fileds.length != headers.length){
                throw new IOException("Files " + file.getName() + " lines" + (i+1) + " invaild");
            }
            List<String> roeData = new ArrayList<>();
            for(int j = 1; j < fileds.length; ++j){
                roeData.add(fileds[j]);
            }
            table.addRow(roeData);
        }
        return table;
    }

    public void saveTable(Table table) throws IOException {
        String fileName = storageFolderPath + File.separator + table.getName().toLowerCase() + ".tab";
        File file = new File(fileName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))){
            List<String> columnNames = table.getColumns();
            writer.write(String.join("\t", columnNames));
            writer.newLine();
            for(List<String> row: table.getRows()){
                writer.write(String.join("\t", row));
                writer.newLine();
            }
        }
    }

    public void saveAllTables(Map<String, Table> tables) throws IOException {
        for(Table table: tables.values()){
            saveTable(table);
        }
    }

    public String getStorageFolderPath() {
        return storageFolderPath;
    }

    public void setStorageFolderPath(String storageFolderPath) {
        this.storageFolderPath = storageFolderPath;
    }
    
}
