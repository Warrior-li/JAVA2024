package edu.uob;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;

/** This class implements the DB server. */
public class DBServer {

    private static final char END_OF_TRANSMISSION = 4;
    private String storageFolderPath;
    private DatabaseManager manager;
    // 内存储存所有的表 键为表名
    private Map<String, Table> tables;
    private String currentDatabase;


    public static void main(String args[]) throws IOException {
        DBServer server = new DBServer();
        server.blockingListenOn(8888);
    }

    /**
    * KEEP this signature otherwise we won't be able to mark your submission correctly.
    */
    public DBServer() {
        storageFolderPath = Paths.get("databases").toAbsolutePath().toString();
        try {
            // Create the database storage folder if it doesn't already exist !
            Files.createDirectories(Paths.get(storageFolderPath));
        } catch(IOException ioe) {
            System.out.println("Can't seem to create database storage folder " + storageFolderPath);
        }
        manager = new DatabaseManager(storageFolderPath);
        tables = new HashMap<>();
        // 加载所有表
        try {
            tables = manager.loadAllTables();
            // 测试输出 应该删除
            System.out.println("已经加载的表" + tables.keySet());
        } catch(IOException e){
            System.out.println("加载时出错");
        }


    }

    /**
    * KEEP this signature (i.e. {@code edu.uob.DBServer.handleCommand(String)}) otherwise we won't be
    * able to mark your submission correctly.
    *
    * <p>This method handles all incoming DB commands and carries out the required actions.
    */

    //
    public String handleCommand(String command) {
        // TODO implement your server logic here
        if (command == null || command.trim().isEmpty()) {
            return "[ERROR] Get empty command";
        }
        String trimmed = command.trim();
        if(!trimmed.endsWith(";")){
            return "[ERROR] Missing semicolon at the end of command";
        }
        // 去掉末尾分号后再 trim
        trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        // 为便于关键字判断，统一转换为大写（保留原始字符串以便提取值）
        String upperCmd = trimmed.toUpperCase();

        try {
            // use 命令
            if (upperCmd.startsWith("USE")) {
                // 格式：USE database name;
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length != 2) {
                    return "[ERROR] USE Command format error";
                }
                String dbName = tokens[1].toLowerCase();
                File dbDir = new File(manager.getStorageFolderPath(), dbName);
                if (!dbDir.exists()) {
                    return "[ERROR] 数据库不存在";
                }
                // 设置当前数据库：更新 manager 的存储路径，并重新加载表数据
                currentDatabase = dbName;  // currentDatabase 为 DBServer 内的字段
                manager.setStorageFolderPath(dbDir.getAbsolutePath());
                tables = manager.loadAllTables();
                return "[OK]";
            }
            // create database
            else if(upperCmd.startsWith("CREATE DATABASE")){
                String[] tokens = trimmed.split("\\s+");
                if(tokens.length != 3){
                    return "[ERROR] CREATE DATABASE Command format error";
                }
                String dbName = tokens[2].toLowerCase();
                File dbDir = new File(manager.getStorageFolderPath(), dbName);
                if(dbDir.exists()){
                    return "[ERROR] Database already exists";
                }
                if (dbDir.mkdir()) {
                    return "[OK]";
                } else {
                    return "[ERROR] Failed to create database";
                }
            }
            // create table command
            else if(upperCmd.startsWith("CREATE TABLE")){
                // 格式：CREATE TABLE tableName (col1, col2, ...);
                int openParen = trimmed.indexOf("(");
                int closeParen = trimmed.indexOf(")");
                if (openParen < 0 || closeParen < 0 || closeParen < openParen) {
                    return "[ERROR] CREATE TABLE Syntax Error";
                }
                String beforeParen = trimmed.substring(0, openParen).trim();
                String[] tokens = beforeParen.split("\\s+");
                if (tokens.length != 3) {
                    return "[ERROR] CREATE TABLE Command format error";
                }
                String tableName = tokens[2].toLowerCase();
                String colsPart = trimmed.substring(openParen + 1, closeParen).trim();
                if (colsPart.isEmpty()) {
                    return "[ERROR] No column name specified";
                }
                String[] cols = colsPart.split(",");
                List<String> colList = new ArrayList<>();
                for (String col : cols) {
                    String colName = col.trim();
                    // 检查是否为 SQL 保留关键字（这里可扩展检查）
                    if (isReservedKeyword(colName)) {
                        return "[ERROR] Column names cannot be reserved keywords: " + colName;
                    }
                    colList.add(colName);
                }
                if (tables.containsKey(tableName)) {
                    return "[ERROR] Table already exists";
                }
                Table table = new Table(tableName, colList);
                tables.put(tableName, table);
                manager.saveTable(table);
                return "[OK]";
            }
            // insert into
             // ----------------- INSERT INTO 命令 -----------------
            else if (upperCmd.startsWith("INSERT INTO")) {
                // 格式：INSERT INTO tableName VALUES (val1, val2, ...);
                int indexValues = upperCmd.indexOf("VALUES");
                if (indexValues < 0) return "[ERROR] INSERT Syntax Error";
                String beforeValues = trimmed.substring(0, indexValues).trim();
                String[] tokens = beforeValues.split("\\s+");
                if (tokens.length != 3) return "[ERROR] INSERT Command format error";
                String tableName = tokens[2].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] Table is not exists";
                int openParen = trimmed.indexOf("(", indexValues);
                int closeParen = trimmed.indexOf(")", openParen);
                if (openParen < 0 || closeParen < 0) return "[ERROR] INSERT Syntax Error";
                String valuesPart = trimmed.substring(openParen + 1, closeParen).trim();
                if (valuesPart.isEmpty()) return "[ERROR] No insert value provided";
                String[] values = valuesPart.split(",");
                List<String> rowData = new ArrayList<>();
                for (String value : values) {
                    value = value.trim();
                    // 去除字符串引号
                    if ((value.startsWith("'") && value.endsWith("'")) ||
                        (value.startsWith("\"") && value.endsWith("\""))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    rowData.add(value);
                }
                try {
                    table.addRow(rowData);
                } catch (Exception e) {
                    return "[ERROR] " + e.getMessage();
                }
                manager.saveTable(table);
                return "[OK]";
            }
            // select
            else if (upperCmd.startsWith("SELECT")) {
                // 格式：SELECT <columns> FROM tableName [WHERE condition]
                int indexFrom = upperCmd.indexOf("FROM");
                if (indexFrom == -1) return "[ERROR] SELECT 语法错误";
                // 提取 SELECT 部分的列定义
                String columnsPart = trimmed.substring(6, indexFrom).trim();
                // 将列名按逗号分割
                List<String> selectedColumns = new ArrayList<>();
                if (columnsPart.equals("*")) {
                    // 如果是 "*"，则选择所有列
                    String tableName = trimmed.substring(indexFrom + 5).split("\\s+")[0].toLowerCase();
                    if (tableName.isEmpty() || !tables.containsKey(tableName)) {
                        // 表名为空或对应表不存在，返回空列表或报错
                        selectedColumns.addAll(new ArrayList<>());
                    } else {
                        selectedColumns.addAll(tables.get(tableName).getColumns());
                    }
                    // 为了后续处理，直接设置标记
                    selectedColumns.clear();
                    selectedColumns.add("*");
                } else {
                    String[] cols = columnsPart.split(",");
                    for (String col : cols) {
                        selectedColumns.add(col.trim());
                    }
                }
                // 处理 FROM 部分：假设格式为 "FROM tableName"
                String fromPart = trimmed.substring(indexFrom).trim();
                String[] fromTokens = fromPart.split("\\s+");
                if (fromTokens.length < 2) return "[ERROR] SELECT 命令格式错误";
                String tableName = fromTokens[1].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] 表不存在";
                
                // 如果 selectedColumns 不为 "*"，则需要验证每个列是否存在，并保存其在 table 中的索引
                List<Integer> selectedIndices = new ArrayList<>();
                if (!(selectedColumns.size() == 1 && selectedColumns.get(0).equals("*"))) {
                    for (String col : selectedColumns) {
                        int idx = -1;
                        for (int i = 0; i < table.getColumns().size(); i++) {
                            if (table.getColumns().get(i).equalsIgnoreCase(col)) {
                                idx = i;
                                break;
                            }
                        }
                        if (idx == -1) return "[ERROR] SELECT 中的列 " + col + " 不存在";
                        selectedIndices.add(idx);
                    }
                }
                
                // 检查是否存在 WHERE 子句（仅支持 == 和 !=）
                boolean hasWhere = upperCmd.contains("WHERE");
                String conditionColumn = null, conditionOp = null, conditionValue = null;
                if (hasWhere) {
                    int indexWhere = upperCmd.indexOf("WHERE");
                    String conditionStr = trimmed.substring(indexWhere + 5).trim();
                    if (conditionStr.contains("==")) {
                        String[] condParts = conditionStr.split("==");
                        if (condParts.length != 2) return "[ERROR] WHERE 条件格式错误";
                        conditionColumn = condParts[0].trim();
                        conditionValue = condParts[1].trim();
                        if ((conditionValue.startsWith("'") && conditionValue.endsWith("'")) ||
                            (conditionValue.startsWith("\"") && conditionValue.endsWith("\""))) {
                            conditionValue = conditionValue.substring(1, conditionValue.length() - 1);
                        }
                        conditionOp = "==";
                    } else if (conditionStr.contains("!=")) {
                        String[] condParts = conditionStr.split("!=");
                        if (condParts.length != 2) return "[ERROR] WHERE 条件格式错误";
                        conditionColumn = condParts[0].trim();
                        conditionValue = condParts[1].trim();
                        if ((conditionValue.startsWith("'") && conditionValue.endsWith("'")) ||
                            (conditionValue.startsWith("\"") && conditionValue.endsWith("\""))) {
                            conditionValue = conditionValue.substring(1, conditionValue.length() - 1);
                        }
                        conditionOp = "!=";
                    } else {
                        return "[ERROR] WHERE 条件不支持";
                    }
                }
                // 构造 SELECT 输出结果
                StringBuilder result = new StringBuilder();
                List<String> header = new ArrayList<>();
                if (selectedColumns.size() == 1 && selectedColumns.get(0).equals("*")) {
                    header.addAll(table.getColumns());
                } else {
                    for (int idx : selectedIndices) {
                        header.add(table.getColumns().get(idx));
                    }
                }
                result.append(String.join("\t", header)).append("\n");
                // 对每一行进行过滤
                for (List<String> row : table.getRows()) {
                    boolean match = true;
                    if (hasWhere) {
                        int colIndex = -1;
                        for (int i = 0; i < table.getColumns().size(); i++) {
                            if (table.getColumns().get(i).equalsIgnoreCase(conditionColumn)) {
                                colIndex = i;
                                break;
                            }
                        }
                        if (colIndex == -1) return "[ERROR] WHERE 条件中的属性不存在";
                        String cellValue = row.get(colIndex);
                        if (conditionOp.equals("==")) {
                            match = cellValue.equals(conditionValue);
                        } else if (conditionOp.equals("!=")) {
                            match = !cellValue.equals(conditionValue);
                        }
                    }
                    if (match) {
                        List<String> rowOutput = new ArrayList<>();
                        if (selectedColumns.size() == 1 && selectedColumns.get(0).equals("*")) {
                            rowOutput.addAll(row);
                        } else {
                            for (int idx : selectedIndices) {
                                rowOutput.add(row.get(idx));
                            }
                        }
                        result.append(String.join("\t", rowOutput)).append("\n");
                    }
                }
                return "[OK]\n" + result.toString();
            }
            // update
            else if (upperCmd.startsWith("UPDATE")) {
                // 格式：UPDATE tableName SET column = value WHERE column == value;
                int indexWhere = upperCmd.indexOf("WHERE");
                if (indexWhere < 0) return "[ERROR] Missing WHERE clause";
                String beforeWhere = trimmed.substring(0, indexWhere).trim();
                String[] tokens = beforeWhere.split("\\s+");
                if (tokens.length < 4) return "[ERROR] UPDATE Command format error";
                String tableName = tokens[1].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] Table does not exist";
                if (!tokens[2].equalsIgnoreCase("SET")) return "[ERROR] Missing SET keyword";
                String setClause = trimmed.substring(trimmed.indexOf("SET") + 3, indexWhere).trim();
                String[] setParts = setClause.split("=");
                if (setParts.length != 2) return "[ERROR] SET clause is malformed";
                String updateColumn = setParts[0].trim();
                String updateValue = setParts[1].trim();
                if ((updateValue.startsWith("'") && updateValue.endsWith("'")) ||
                    (updateValue.startsWith("\"") && updateValue.endsWith("\""))) {
                    updateValue = updateValue.substring(1, updateValue.length() - 1);
                }
                String conditionClause = trimmed.substring(indexWhere + 5).trim();
                String conditionColumn = null, conditionValue = null;
                if (conditionClause.contains("==")) {
                    String[] condParts = conditionClause.split("==");
                    if (condParts.length != 2) return "[ERROR] WHERE clause is malformed";
                    conditionColumn = condParts[0].trim();
                    conditionValue = condParts[1].trim();
                    if ((conditionValue.startsWith("'") && conditionValue.endsWith("'")) ||
                        (conditionValue.startsWith("\"") && conditionValue.endsWith("\""))) {
                        conditionValue = conditionValue.substring(1, conditionValue.length() - 1);
                    }
                } else {
                    return "[ERROR] UPDATE WHERE Clause conditions are not supported";
                }
                int updateColIndex = -1, conditionColIndex = -1;
                for (int i = 0; i < table.getColumns().size(); i++) {
                    String col = table.getColumns().get(i);
                    if (col.equalsIgnoreCase(updateColumn)) updateColIndex = i;
                    if (col.equalsIgnoreCase(conditionColumn)) conditionColIndex = i;
                }
                if (updateColIndex == -1) return "[ERROR] The updated attribute does not exist";
                if (conditionColIndex == -1) return "[ERROR] WHERE The attribute in the clause does not exist";
                for (List<String> row : table.getRows()) {
                    if (row.get(conditionColIndex).equals(conditionValue)) {
                        row.set(updateColIndex, updateValue);
                    }
                }
                manager.saveTable(table);
                return "[OK]";
            }
            // delete
            else if (upperCmd.startsWith("DELETE")) {
                // 格式：DELETE FROM tableName WHERE column == value;
                int indexWhere = upperCmd.indexOf("WHERE");
                if (indexWhere < 0) return "[ERROR] DELETE 命令缺少 WHERE 子句";
                String beforeWhere = trimmed.substring(0, indexWhere).trim();
                String[] tokens = beforeWhere.split("\\s+");
                if (tokens.length != 3) return "[ERROR] DELETE 命令格式错误";
                if (!tokens[0].equalsIgnoreCase("DELETE") || !tokens[1].equalsIgnoreCase("FROM"))
                    return "[ERROR] DELETE 命令语法错误";
                String tableName = tokens[2].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] 表不存在";
                String conditionClause = trimmed.substring(indexWhere + 5).trim();
                String conditionColumn = null, conditionValue = null;
                if (conditionClause.contains("==")) {
                    String[] condParts = conditionClause.split("==");
                    if (condParts.length != 2) return "[ERROR] WHERE 子句格式错误";
                    conditionColumn = condParts[0].trim();
                    conditionValue = condParts[1].trim();
                    if ((conditionValue.startsWith("'") && conditionValue.endsWith("'")) ||
                        (conditionValue.startsWith("\"") && conditionValue.endsWith("\""))) {
                        conditionValue = conditionValue.substring(1, conditionValue.length() - 1);
                    }
                } else {
                    return "[ERROR] DELETE 命令的条件不支持";
                }
                int conditionColIndex = -1;
                for (int i = 0; i < table.getColumns().size(); i++) {
                    if (table.getColumns().get(i).equalsIgnoreCase(conditionColumn)) {
                        conditionColIndex = i;
                        break;
                    }
                }
                if (conditionColIndex == -1) return "[ERROR] WHERE 子句中的属性不存在";
                for (int i = table.getRows().size() - 1; i >= 0; i--) {
                    List<String> row = table.getRows().get(i);
                    if (row.get(conditionColIndex).equals(conditionValue)) {
                        table.getRows().remove(i);
                    }
                }
                manager.saveTable(table);
                return "[OK]";
            }
            // alter table
            else if (upperCmd.startsWith("ALTER")) {
                // 格式：ALTER TABLE tableName ADD columnName  或  ALTER TABLE tableName DROP columnName;
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length != 5) return "[ERROR] ALTER TABLE 命令格式错误";
                if (!tokens[1].equalsIgnoreCase("TABLE")) return "[ERROR] 缺少 TABLE 关键字";
                String tableName = tokens[2].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] 表不存在";
                String operation = tokens[3].toUpperCase();
                String columnName = tokens[4];
                if (operation.equals("ADD")) {
                    // 检查是否已有该列
                    for (String col : table.getColumns()) {
                        if (col.equalsIgnoreCase(columnName)) {
                            return "[ERROR] 列已存在";
                        }
                    }
                    table.getColumns().add(columnName);
                    // 为所有记录增加空字符串
                    for (List<String> row : table.getRows()) {
                        row.add("");
                    }
                    manager.saveTable(table);
                    return "[OK]";
                } else if (operation.equals("DROP")) {
                    if (columnName.equalsIgnoreCase("id")) {
                        return "[ERROR] 无法删除主键列";
                    }
                    int dropIndex = -1;
                    for (int i = 0; i < table.getColumns().size(); i++) {
                        if (table.getColumns().get(i).equalsIgnoreCase(columnName)) {
                            dropIndex = i;
                            break;
                        }
                    }
                    if (dropIndex == -1) return "[ERROR] 列不存在";
                    table.getColumns().remove(dropIndex);
                    for (List<String> row : table.getRows()) {
                        row.remove(dropIndex);
                    }
                    manager.saveTable(table);
                    return "[OK]";
                } else {
                    return "[ERROR] ALTER TABLE 操作不支持";
                }
            }
            // drop
            else if (upperCmd.startsWith("DROP")) {
                // 格式：DROP TABLE tableName  或  DROP DATABASE databaseName;
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length != 3) return "[ERROR] DROP 命令格式错误";
                String dropType = tokens[1].toUpperCase();
                String targetName = tokens[2].toLowerCase();
                if (dropType.equals("TABLE")) {
                    if (!tables.containsKey(targetName)) return "[ERROR] 表不存在";
                    tables.remove(targetName);
                    File file = new File(manager.getStorageFolderPath(), targetName + ".tab");
                    if (file.exists()) {
                        file.delete();
                    }
                    return "[OK]";
                } else if (dropType.equals("DATABASE")) {
                    File dbDir = new File(manager.getStorageFolderPath(), targetName);
                    if (!dbDir.exists()) return "[ERROR] 数据库不存在";
                    for (File f : dbDir.listFiles()) {
                        f.delete();
                    }
                    dbDir.delete();
                    if (currentDatabase != null && currentDatabase.equals(targetName)) {
                        currentDatabase = null;
                        tables = new HashMap<>();
                    }
                    return "[OK]";
                } else {
                    return "[ERROR] DROP 操作不支持";
                }
            }
            // join
            else if (upperCmd.startsWith("JOIN")) {
                // 格式：JOIN tableOne AND tableTwo ON attributeFromTableOne AND attributeFromTableTwo;
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length != 8) return "[ERROR] JOIN 命令格式错误";
                String tableOneName = tokens[1].toLowerCase();
                String tableTwoName = tokens[3].toLowerCase();
                String attrOne = tokens[5];
                String attrTwo = tokens[7];
                Table tableOne = tables.get(tableOneName);
                Table tableTwo = tables.get(tableTwoName);
                if (tableOne == null || tableTwo == null) return "[ERROR] 一个或多个表不存在";
                int indexOne = -1, indexTwo = -1;
                for (int i = 0; i < tableOne.getColumns().size(); i++) {
                    if (tableOne.getColumns().get(i).equalsIgnoreCase(attrOne)) {
                        indexOne = i;
                        break;
                    }
                }
                for (int i = 0; i < tableTwo.getColumns().size(); i++) {
                    if (tableTwo.getColumns().get(i).equalsIgnoreCase(attrTwo)) {
                        indexTwo = i;
                        break;
                    }
                }
                if (indexOne == -1) return "[ERROR] 表 " + tableOneName + " 中不存在属性 " + attrOne;
                if (indexTwo == -1) return "[ERROR] 表 " + tableTwoName + " 中不存在属性 " + attrTwo;
                StringBuilder joinResult = new StringBuilder();
                List<String> joinColumns = new ArrayList<>();
                // 添加 tableOne 的列（排除 id 和 attrOne）
                for (int i = 0; i < tableOne.getColumns().size(); i++) {
                    String col = tableOne.getColumns().get(i);
                    if (i == 0 || col.equalsIgnoreCase(attrOne)) continue;
                    joinColumns.add(tableOneName + "." + col);
                }
                // 添加 tableTwo 的列（排除 id 和 attrTwo）
                for (int i = 0; i < tableTwo.getColumns().size(); i++) {
                    String col = tableTwo.getColumns().get(i);
                    if (i == 0 || col.equalsIgnoreCase(attrTwo)) continue;
                    joinColumns.add(tableTwoName + "." + col);
                }
                joinResult.append("id").append("\t").append(String.join("\t", joinColumns)).append("\n");
                int newId = 1;
                for (List<String> rowOne : tableOne.getRows()) {
                    for (List<String> rowTwo : tableTwo.getRows()) {
                        if (rowOne.get(indexOne).equals(rowTwo.get(indexTwo))) {
                            List<String> joinRow = new ArrayList<>();
                            joinRow.add(String.valueOf(newId++));
                            for (int i = 0; i < rowOne.size(); i++) {
                                if (i == 0 || tableOne.getColumns().get(i).equalsIgnoreCase(attrOne)) continue;
                                joinRow.add(rowOne.get(i));
                            }
                            for (int i = 0; i < rowTwo.size(); i++) {
                                if (i == 0 || tableTwo.getColumns().get(i).equalsIgnoreCase(attrTwo)) continue;
                                joinRow.add(rowTwo.get(i));
                            }
                            joinResult.append(String.join("\t", joinRow)).append("\n");
                        }
                    }
                }
                return "[OK]\n" + joinResult.toString();
            }
            else {
                return "[ERROR] 未知命令";
            }
        } catch (IOException e){
            return "IO exception: " + e.getMessage();
        }
    }
    
    private boolean isReservedKeyword(String word) {
        // 定义一组常见的 SQL 保留关键字
        String[] reserved = {"SELECT", "INSERT", "UPDATE", "DELETE", "FROM", "WHERE",
                             "JOIN", "CREATE", "DATABASE", "TABLE", "DROP", "ALTER", 
                             "USE", "AND", "OR", "TRUE", "FALSE", "LIKE", "NOT"};
        for (String kw : reserved) {
            if (kw.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }    

    //  === Methods below handle networking aspects of the project - you will not need to change these ! ===

    public void blockingListenOn(int portNumber) throws IOException {
        try (ServerSocket s = new ServerSocket(portNumber)) {
            System.out.println("Server listening on port " + portNumber);
            while (!Thread.interrupted()) {
                try {
                    blockingHandleConnection(s);
                } catch (IOException e) {
                    System.err.println("Server encountered a non-fatal IO error:");
                    e.printStackTrace();
                    System.err.println("Continuing...");
                }
            }
        }
    }

    private void blockingHandleConnection(ServerSocket serverSocket) throws IOException {
        try (Socket s = serverSocket.accept();
        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {

            System.out.println("Connection established: " + serverSocket.getInetAddress());
            while (!Thread.interrupted()) {
                String incomingCommand = reader.readLine();
                System.out.println("Received message: " + incomingCommand);
                String result = handleCommand(incomingCommand);
                writer.write(result);
                writer.write("\n" + END_OF_TRANSMISSION + "\n");
                writer.flush();
            }
        }
    }
}
