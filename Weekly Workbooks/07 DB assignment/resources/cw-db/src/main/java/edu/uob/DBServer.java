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
        if (command == null || command.trim().isEmpty()) {
            return "[ERROR] Empty command received";
        }
        // Trim and check that the command ends with a semicolon
        String trimmed = command.trim();
        if (!trimmed.endsWith(";")) {
            return "[ERROR] Missing semicolon at the end of the command";
        }
        // Remove the trailing semicolon and trim again
        trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        // For keyword comparisons, convert to uppercase but preserve the original for value extraction
        String upperCmd = trimmed.toUpperCase();
        
        try {
            // ----------------- USE Command -----------------
            if (upperCmd.startsWith("USE")) {
                // Format: USE databaseName;
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length != 2) {
                    return "[ERROR] Incorrect USE command format";
                }
                String dbName = tokens[1].toLowerCase();
                File dbDir = new File(manager.getStorageFolderPath(), dbName);
                if (!dbDir.exists()) {
                    return "[ERROR] Database does not exist";
                }
                // Set current database: update manager storage path and reload tables
                currentDatabase = dbName; // currentDatabase is a field in DBServer
                manager.setStorageFolderPath(dbDir.getAbsolutePath());
                tables = manager.loadAllTables();
                return "[OK]";
            }
            // ----------------- CREATE DATABASE Command -----------------
            else if (upperCmd.startsWith("CREATE DATABASE")) {
                // Format: CREATE DATABASE databaseName;
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length != 3) {
                    return "[ERROR] Incorrect CREATE DATABASE command format";
                }
                String dbName = tokens[2].toLowerCase();
                File dbDir = new File(manager.getStorageFolderPath(), dbName);
                if (dbDir.exists()) {
                    return "[ERROR] Database already exists";
                }
                if (dbDir.mkdir()) {
                    return "[OK]";
                } else {
                    return "[ERROR] Failed to create database";
                }
            }
            // ----------------- CREATE TABLE Command -----------------
            else if (upperCmd.startsWith("CREATE TABLE")) {
                // Format: CREATE TABLE tableName [(col1, col2, ...)];
                int openParen = trimmed.indexOf("(");
                int closeParen = trimmed.indexOf(")");
                String tableName;
                List<String> colList = new ArrayList<>();
                if (openParen == -1 || closeParen == -1) {
                    // No parentheses: create an empty table (only id column)
                    String[] tokens = trimmed.split("\\s+");
                    if (tokens.length != 3) {
                        return "[ERROR] Incorrect CREATE TABLE command format";
                    }
                    tableName = tokens[2].toLowerCase();
                } else {
                    // Parentheses exist: parse the column list
                    String beforeParen = trimmed.substring(0, openParen).trim();
                    String[] tokens = beforeParen.split("\\s+");
                    if (tokens.length != 3) {
                        return "[ERROR] Incorrect CREATE TABLE command format";
                    }
                    tableName = tokens[2].toLowerCase();
                    String colsPart = trimmed.substring(openParen + 1, closeParen).trim();
                    if (colsPart.isEmpty()) {
                        return "[ERROR] No columns specified";
                    }
                    String[] cols = colsPart.split(",");
                    for (String col : cols) {
                        String colName = col.trim();
                        if (isReservedKeyword(colName)) {
                            return "[ERROR] Column name cannot be a reserved keyword: " + colName;
                        }
                        colList.add(colName);
                    }
                }
                if (tables.containsKey(tableName)) {
                    return "[ERROR] Table already exists";
                }
                Table table = new Table(tableName, colList);
                tables.put(tableName, table);
                manager.saveTable(table);
                return "[OK]";
            }
            // ----------------- INSERT INTO Command -----------------
            else if (upperCmd.startsWith("INSERT INTO")) {
                // Format: INSERT INTO tableName VALUES (val1, val2, ...);
                int indexValues = upperCmd.indexOf("VALUES");
                if (indexValues == -1) return "[ERROR] INSERT syntax error";
                String beforeValues = trimmed.substring(0, indexValues).trim();
                String[] tokens = beforeValues.split("\\s+");
                if (tokens.length != 3) return "[ERROR] Incorrect INSERT command format";
                String tableName = tokens[2].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] Table does not exist";
                int openParen = trimmed.indexOf('(', indexValues);
                int closeParen = trimmed.indexOf(')', openParen);
                if (openParen == -1 || closeParen == -1) return "[ERROR] INSERT syntax error";
                String valuesPart = trimmed.substring(openParen + 1, closeParen).trim();
                if (valuesPart.isEmpty()) return "[ERROR] No values provided";
                String[] values = valuesPart.split(",");
                List<String> rowData = new ArrayList<>();
                for (String value : values) {
                    value = value.trim();
                    // Remove string quotes if present
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
            // ----------------- SELECT Command -----------------
            else if (upperCmd.startsWith("SELECT")) {
                // Format: SELECT <columns> FROM tableName [WHERE condition];
                int indexFrom = upperCmd.indexOf("FROM");
                if (indexFrom == -1) return "[ERROR] SELECT syntax error";
                String columnsPart = trimmed.substring(6, indexFrom).trim(); // Extract between "SELECT" and "FROM"
                List<String> selectedColumns = new ArrayList<>();
                boolean selectAll = false;
                if (columnsPart.equals("*")) {
                    selectAll = true;
                } else {
                    String[] cols = columnsPart.split(",");
                    for (String col : cols) {
                        selectedColumns.add(col.trim());
                    }
                }
                // Process FROM part: extract table name
                String afterFrom = trimmed.substring(indexFrom + 4).trim();
                String[] fromTokens = afterFrom.split("\\s+");
                if (fromTokens.length < 1) return "[ERROR] SELECT command format error";
                String tableName = fromTokens[0].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] Table does not exist";
                
                // Determine selected column indices and header
                List<Integer> selectedIndices = new ArrayList<>();
                List<String> header = new ArrayList<>();
                if (selectAll) {
                    header.addAll(table.getColumns());
                } else {
                    for (String col : selectedColumns) {
                        int idx = -1;
                        for (int i = 0; i < table.getColumns().size(); i++) {
                            if (table.getColumns().get(i).equalsIgnoreCase(col)) {
                                idx = i;
                                break;
                            }
                        }
                        if (idx == -1) return "[ERROR] SELECT column " + col + " does not exist";
                        selectedIndices.add(idx);
                        header.add(table.getColumns().get(idx));
                    }
                }
                
                // Process WHERE clause (supporting compound conditions connected by AND)
                List<Condition> conditions = new ArrayList<>();
                if (upperCmd.contains("WHERE")) {
                    int indexWhere = upperCmd.indexOf("WHERE");
                    String whereClause = trimmed.substring(indexWhere + 5).trim();
                    // Split conditions by "AND", case-insensitive
                    String[] condStrings = whereClause.split("(?i)\\s+AND\\s+");
                    for (String condStr : condStrings) {
                        condStr = condStr.trim();
                        // Remove surrounding parentheses if present
                        if (condStr.startsWith("(") && condStr.endsWith(")")) {
                            condStr = condStr.substring(1, condStr.length() - 1).trim();
                        }
                        Condition cond = parseCondition(condStr);
                        if (cond == null) return "[ERROR] Invalid WHERE condition: " + condStr;
                        // Verify that the attribute exists in the table
                        boolean attrFound = false;
                        for (String col : table.getColumns()) {
                            if (col.equalsIgnoreCase(cond.attribute)) {
                                attrFound = true;
                                break;
                            }
                        }
                        if (!attrFound) return "[ERROR] WHERE clause attribute does not exist: " + cond.attribute;
                        conditions.add(cond);
                    }
                }
                
                // Build result by iterating through rows and applying all conditions
                StringBuilder result = new StringBuilder();
                result.append(String.join("\t", header)).append("\n");
                for (List<String> row : table.getRows()) {
                    boolean include = true;
                    for (Condition cond : conditions) {
                        int colIndex = -1;
                        for (int i = 0; i < table.getColumns().size(); i++) {
                            if (table.getColumns().get(i).equalsIgnoreCase(cond.attribute)) {
                                colIndex = i;
                                break;
                            }
                        }
                        if (colIndex == -1) return "[ERROR] WHERE clause attribute does not exist: " + cond.attribute;
                        String cellValue = row.get(colIndex);
                        if (!evaluateCondition(cellValue, cond)) {
                            include = false;
                            break;
                        }
                    }
                    if (include) {
                        List<String> rowOutput = new ArrayList<>();
                        if (selectAll) {
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
            // ----------------- UPDATE Command -----------------
            else if (upperCmd.startsWith("UPDATE")) {
                // Format: UPDATE tableName SET column = value WHERE column == value;
                int indexWhere = upperCmd.indexOf("WHERE");
                if (indexWhere < 0) return "[ERROR] Missing WHERE clause in UPDATE";
                String beforeWhere = trimmed.substring(0, indexWhere).trim();
                String[] tokens = beforeWhere.split("\\s+");
                if (tokens.length < 4) return "[ERROR] Incorrect UPDATE command format";
                String tableName = tokens[1].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] Table does not exist";
                if (!tokens[2].equalsIgnoreCase("SET")) return "[ERROR] Missing SET keyword in UPDATE";
                String setClause = trimmed.substring(trimmed.indexOf("SET") + 3, indexWhere).trim();
                String[] setParts = setClause.split("=");
                if (setParts.length != 2) return "[ERROR] SET clause format error";
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
                    if (condParts.length != 2) return "[ERROR] WHERE clause format error in UPDATE";
                    conditionColumn = condParts[0].trim();
                    conditionValue = condParts[1].trim();
                    if ((conditionValue.startsWith("'") && conditionValue.endsWith("'")) ||
                        (conditionValue.startsWith("\"") && conditionValue.endsWith("\""))) {
                        conditionValue = conditionValue.substring(1, conditionValue.length() - 1);
                    }
                } else {
                    return "[ERROR] Unsupported condition in UPDATE WHERE clause";
                }
                int updateColIndex = -1, conditionColIndex = -1;
                for (int i = 0; i < table.getColumns().size(); i++) {
                    String col = table.getColumns().get(i);
                    if (col.equalsIgnoreCase(updateColumn)) updateColIndex = i;
                    if (col.equalsIgnoreCase(conditionColumn)) conditionColIndex = i;
                }
                if (updateColIndex == -1) return "[ERROR] Update attribute does not exist";
                if (conditionColIndex == -1) return "[ERROR] WHERE clause attribute does not exist in UPDATE";
                for (List<String> row : table.getRows()) {
                    if (row.get(conditionColIndex).equals(conditionValue)) {
                        row.set(updateColIndex, updateValue);
                    }
                }
                manager.saveTable(table);
                return "[OK]";
            }
            // ----------------- DELETE Command -----------------
            else if (upperCmd.startsWith("DELETE")) {
                // Format: DELETE FROM tableName WHERE condition;
                int indexWhere = upperCmd.indexOf("WHERE");
                if (indexWhere < 0) return "[ERROR] DELETE command missing WHERE clause";
                String beforeWhere = trimmed.substring(0, indexWhere).trim();
                String[] tokens = beforeWhere.split("\\s+");
                if (tokens.length != 3) return "[ERROR] DELETE command format error";
                if (!tokens[0].equalsIgnoreCase("DELETE") || !tokens[1].equalsIgnoreCase("FROM"))
                    return "[ERROR] DELETE command syntax error";
                String tableName = tokens[2].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] Table does not exist";
                
                String whereClause = trimmed.substring(indexWhere + 5).trim();
                // Split conditions by "AND" (case-insensitive)
                String[] condStrings = whereClause.split("(?i)\\s+AND\\s+");
                List<Condition> conditions = new ArrayList<>();
                for (String condStr : condStrings) {
                    condStr = condStr.trim();
                    // Remove enclosing parentheses if present
                    if (condStr.startsWith("(") && condStr.endsWith(")")) {
                        condStr = condStr.substring(1, condStr.length() - 1).trim();
                    }
                    Condition cond = parseCondition(condStr);
                    if (cond == null) return "[ERROR] Invalid WHERE condition: " + condStr;
                    // Check if the attribute exists in the table
                    boolean attrFound = false;
                    for (String col : table.getColumns()) {
                        if (col.equalsIgnoreCase(cond.attribute)) {
                            attrFound = true;
                            break;
                        }
                    }
                    if (!attrFound) return "[ERROR] WHERE clause attribute does not exist: " + cond.attribute;
                    conditions.add(cond);
                }
                
                // Iterate through rows in reverse and remove those that satisfy all conditions.
                for (int i = table.getRows().size() - 1; i >= 0; i--) {
                    List<String> row = table.getRows().get(i);
                    boolean match = true;
                    for (Condition cond : conditions) {
                        int colIndex = -1;
                        for (int j = 0; j < table.getColumns().size(); j++) {
                            if (table.getColumns().get(j).equalsIgnoreCase(cond.attribute)) {
                                colIndex = j;
                                break;
                            }
                        }
                        if (colIndex == -1) {
                            match = false;
                            break;
                        }
                        String cellValue = row.get(colIndex);
                        if (!evaluateCondition(cellValue, cond)) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        table.getRows().remove(i);
                    }
                }
                manager.saveTable(table);
                return "[OK]";
            }
            // ----------------- ALTER TABLE Command -----------------
            else if (upperCmd.startsWith("ALTER")) {
                // Format: ALTER TABLE tableName ADD columnName  OR  ALTER TABLE tableName DROP columnName;
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length != 5) return "[ERROR] Incorrect ALTER TABLE command format";
                if (!tokens[1].equalsIgnoreCase("TABLE")) return "[ERROR] Missing TABLE keyword in ALTER TABLE";
                String tableName = tokens[2].toLowerCase();
                Table table = tables.get(tableName);
                if (table == null) return "[ERROR] Table does not exist";
                String operation = tokens[3].toUpperCase();
                String columnName = tokens[4];
                if (operation.equals("ADD")) {
                    for (String col : table.getColumns()) {
                        if (col.equalsIgnoreCase(columnName)) {
                            return "[ERROR] Column already exists";
                        }
                    }
                    table.getColumns().add(columnName);
                    for (List<String> row : table.getRows()) {
                        row.add("");
                    }
                    manager.saveTable(table);
                    return "[OK]";
                } else if (operation.equals("DROP")) {
                    if (columnName.equalsIgnoreCase("id")) {
                        return "[ERROR] Cannot drop primary key column";
                    }
                    int dropIndex = -1;
                    for (int i = 0; i < table.getColumns().size(); i++) {
                        if (table.getColumns().get(i).equalsIgnoreCase(columnName)) {
                            dropIndex = i;
                            break;
                        }
                    }
                    if (dropIndex == -1) return "[ERROR] Column does not exist";
                    table.getColumns().remove(dropIndex);
                    for (List<String> row : table.getRows()) {
                        row.remove(dropIndex);
                    }
                    manager.saveTable(table);
                    return "[OK]";
                } else {
                    return "[ERROR] ALTER TABLE operation not supported";
                }
            }
            // ----------------- DROP Command -----------------
            else if (upperCmd.startsWith("DROP")) {
                // Format: DROP TABLE tableName OR DROP DATABASE databaseName;
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length != 3) return "[ERROR] Incorrect DROP command format";
                String dropType = tokens[1].toUpperCase();
                String targetName = tokens[2].toLowerCase();
                if (dropType.equals("TABLE")) {
                    if (!tables.containsKey(targetName)) return "[ERROR] Table does not exist";
                    tables.remove(targetName);
                    File file = new File(manager.getStorageFolderPath(), targetName + ".tab");
                    if (file.exists()) {
                        file.delete();
                    }
                    return "[OK]";
                } else if (dropType.equals("DATABASE")) {
                    File dbDir = new File(manager.getStorageFolderPath(), targetName);
                    if (!dbDir.exists()) return "[ERROR] Database does not exist";
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
                    return "[ERROR] DROP operation not supported";
                }
            }
            // ----------------- JOIN Command -----------------
            else if (upperCmd.startsWith("JOIN")) {
                // Format: JOIN tableOne AND tableTwo ON attributeFromTableOne AND attributeFromTableTwo;
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length != 8) return "[ERROR] Incorrect JOIN command format";
                String tableOneName = tokens[1].toLowerCase();
                String tableTwoName = tokens[3].toLowerCase();
                String attrOne = tokens[5];
                String attrTwo = tokens[7];
                Table tableOne = tables.get(tableOneName);
                Table tableTwo = tables.get(tableTwoName);
                if (tableOne == null || tableTwo == null) return "[ERROR] One or both tables do not exist";
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
                if (indexOne == -1) return "[ERROR] Table " + tableOneName + " does not have attribute " + attrOne;
                if (indexTwo == -1) return "[ERROR] Table " + tableTwoName + " does not have attribute " + attrTwo;
                StringBuilder joinResult = new StringBuilder();
                List<String> joinColumns = new ArrayList<>();
                // Add columns from tableOne (excluding id and attrOne)
                for (int i = 0; i < tableOne.getColumns().size(); i++) {
                    String col = tableOne.getColumns().get(i);
                    if (i == 0 || col.equalsIgnoreCase(attrOne)) continue;
                    joinColumns.add(tableOneName + "." + col);
                }
                // Add columns from tableTwo (excluding id and attrTwo)
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
                return "[ERROR] Unknown command";
            }
        } catch (IOException e) {
            return "[ERROR] IO Exception: " + e.getMessage();
        }
    }
    
    
    private boolean isReservedKeyword(String word) {
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

    // Helper class to represent a simple condition in the WHERE clause.
    private static class Condition {
        String attribute;
        String comparator;
        String value;
    }

    // Helper method to parse a single condition string.
    private Condition parseCondition(String condStr) {
        // Include "LIKE" among the comparators. Check longer comparators first.
        String[] comparators = {"==", "!=", ">=", "<=", "LIKE", ">", "<"};
        for (String comp : comparators) {
            // For "LIKE", allow it to be surrounded by spaces or not.
            int idx = condStr.indexOf(comp);
            if (idx != -1) {
                Condition cond = new Condition();
                cond.attribute = condStr.substring(0, idx).trim();
                cond.comparator = comp.trim();  // For "LIKE", this will be "LIKE"
                cond.value = condStr.substring(idx + comp.length()).trim();
                // Remove quotes from the value if present
                if ((cond.value.startsWith("'") && cond.value.endsWith("'")) ||
                    (cond.value.startsWith("\"") && cond.value.endsWith("\""))) {
                    cond.value = cond.value.substring(1, cond.value.length() - 1);
                }
                return cond;
            }
        }
        return null; // unsupported condition
    }

    // Helper method to evaluate a single condition against a cell value.
    private boolean evaluateCondition(String cellValue, Condition cond) {
        try {
            // Try numeric comparison if possible.
            double cellNum = Double.parseDouble(cellValue);
            double condNum = Double.parseDouble(cond.value);
            switch (cond.comparator) {
                case "==": return cellNum == condNum;
                case "!=": return cellNum != condNum;
                case ">": return cellNum > condNum;
                case "<": return cellNum < condNum;
                case ">=": return cellNum >= condNum;
                case "<=": return cellNum <= condNum;
                // "LIKE" is not numeric; fall through.
                default: break;
            }
        } catch (NumberFormatException e) {
            // Not numbers, fallback to string comparison.
        }
        // For string comparisons.
        switch (cond.comparator) {
            case "==": return cellValue.equals(cond.value);
            case "!=": return !cellValue.equals(cond.value);
            case ">": return cellValue.compareTo(cond.value) > 0;
            case "<": return cellValue.compareTo(cond.value) < 0;
            case ">=": return cellValue.compareTo(cond.value) >= 0;
            case "<=": return cellValue.compareTo(cond.value) <= 0;
            case "LIKE": return cellValue.contains(cond.value);
            default: return false;
        }
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
