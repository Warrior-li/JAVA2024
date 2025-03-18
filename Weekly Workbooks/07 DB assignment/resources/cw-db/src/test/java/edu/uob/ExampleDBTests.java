package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;

public class ExampleDBTests {

    private DBServer server;

    // Create a new server _before_ every @Test
    @BeforeEach
    public void setup() {
        server = new DBServer();
    }

    // Random name generator - useful for testing "bare earth" queries (i.e. where tables don't previously exist)
    private String generateRandomName() {
        String randomName = "";
        for(int i=0; i<10 ;i++) randomName += (char)( 97 + (Math.random() * 25.0));
        return randomName;
    }

    private String sendCommandToServer(String command) {
        // Try to send a command to the server - this call will timeout if it takes too long (in case the server enters an infinite loop)
        return assertTimeoutPreemptively(Duration.ofMillis(1000), () -> { return server.handleCommand(command);},
        "Server took too long to respond (probably stuck in an infinite loop)");
    }

    // A basic test that creates a database, creates a table, inserts some test data, then queries it.
    // It then checks the response to see that a couple of the entries in the table are returned as expected
    @Test
    public void testBasicCreateAndQuery() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("INSERT INTO marks VALUES ('Simon', 65, TRUE);");
        sendCommandToServer("INSERT INTO marks VALUES ('Sion', 55, TRUE);");
        sendCommandToServer("INSERT INTO marks VALUES ('Rob', 35, FALSE);");
        sendCommandToServer("INSERT INTO marks VALUES ('Chris', 20, FALSE);");
        String response = sendCommandToServer("SELECT * FROM marks;");
        assertTrue(response.contains("[OK]"), "A valid query was made, however an [OK] tag was not returned");
        assertFalse(response.contains("[ERROR]"), "A valid query was made, however an [ERROR] tag was returned");
        assertTrue(response.contains("Simon"), "An attempt was made to add Simon to the table, but they were not returned by SELECT *");
        assertTrue(response.contains("Chris"), "An attempt was made to add Chris to the table, but they were not returned by SELECT *");
    }

    // A test to make sure that querying returns a valid ID (this test also implicitly checks the "==" condition)
    // (these IDs are used to create relations between tables, so it is essential that suitable IDs are being generated and returned !)
    @Test
    public void testQueryID() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("INSERT INTO marks VALUES ('Simon', 65, TRUE);");
        String response = sendCommandToServer("SELECT id FROM marks WHERE name == 'Simon';");
        // Convert multi-lined responses into just a single line
        String singleLine = response.replace("\n"," ").trim();
        // Split the line on the space character
        String[] tokens = singleLine.split(" ");
        // Check that the very last token is a number (which should be the ID of the entry)
        String lastToken = tokens[tokens.length-1];
        try {
            Integer.parseInt(lastToken);
        } catch (NumberFormatException nfe) {
            fail("The last token returned by `SELECT id FROM marks WHERE name == 'Simon';` should have been an integer ID, but was " + lastToken);
        }
    }

    // A test to make sure that databases can be reopened after server restart
    @Test
    public void testTablePersistsAfterRestart() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("INSERT INTO marks VALUES ('Simon', 65, TRUE);");
        // Create a new server object
        server = new DBServer();
        sendCommandToServer("USE " + randomName + ";");
        String response = sendCommandToServer("SELECT * FROM marks;");
        assertTrue(response.contains("Simon"), "Simon was added to a table and the server restarted - but Simon was not returned by SELECT *");
    }

    // Test to make sure that the [ERROR] tag is returned in the case of an error (and NOT the [OK] tag)
    @Test
    public void testForErrorTag() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("INSERT INTO marks VALUES ('Simon', 65, TRUE);");
        String response = sendCommandToServer("SELECT * FROM libraryfines;");
        assertTrue(response.contains("[ERROR]"), "An attempt was made to access a non-existent table, however an [ERROR] tag was not returned");
        assertFalse(response.contains("[OK]"), "An attempt was made to access a non-existent table, however an [OK] tag was returned");
    }

    // Test UPDATE command: modify a record and verify the change.
    @Test
    public void testUpdateQuery() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("INSERT INTO marks VALUES ('Alice', 50, FALSE);");
        sendCommandToServer("UPDATE marks SET mark = 75 WHERE name == 'Alice';");
        String response = sendCommandToServer("SELECT mark FROM marks WHERE name == 'Alice';");
        assertTrue(response.contains("75"), "After update, mark should be 75 but response was: " + response);
    }

    // Test DELETE command: remove a record and verify it is removed.
    @Test
    public void testDeleteQuery() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("INSERT INTO marks VALUES ('Bob', 80, TRUE);");
        sendCommandToServer("DELETE FROM marks WHERE name == 'Bob';");
        String response = sendCommandToServer("SELECT * FROM marks;");
        assertFalse(response.contains("Bob"), "'Bob' should have been deleted, but still appears in the SELECT result.");
    }

    // Test ALTER TABLE ADD: add a new column and verify it appears in the table header.
    @Test
    public void testAlterTableAddColumn() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("ALTER TABLE marks ADD age;");
        String response = sendCommandToServer("SELECT * FROM marks;");
        String header = response.split("\n")[1];
        assertTrue(header.contains("age"), "After ALTER TABLE ADD, header should include 'age'. Actual header: " + header);
    }

    // Test ALTER TABLE DROP: drop an existing column and verify it is removed.
    @Test
    public void testAlterTableDropColumn() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        // First add a new column
        sendCommandToServer("ALTER TABLE marks ADD age;");
        // Then drop an existing column 'pass'
        sendCommandToServer("ALTER TABLE marks DROP pass;");
        String response = sendCommandToServer("SELECT * FROM marks;");
        String header = response.split("\n")[1];
        assertFalse(header.contains("pass"), "After ALTER TABLE DROP, 'pass' should be removed. Header: " + header);
    }

    // Test JOIN command: join two tables and verify the result.
    @Test
    public void testJoinQuery() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE coursework (task, submission);");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("INSERT INTO coursework VALUES ('OXO', 1);");
        sendCommandToServer("INSERT INTO coursework VALUES ('DB', 2);");
        sendCommandToServer("INSERT INTO marks VALUES ('Alice', 90, TRUE);"); // generated id 1
        sendCommandToServer("INSERT INTO marks VALUES ('Bob', 80, FALSE);");   // generated id 2
        String response = sendCommandToServer("JOIN coursework AND marks ON submission AND id;");
        assertTrue(response.contains("Alice") || response.contains("Bob"),
                "JOIN result should contain at least one record. Response: " + response);
    }

    // Test DROP TABLE: drop an existing table and verify subsequent queries return error.
    @Test
    public void testDropTable() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("DROP TABLE marks;");
        String response = sendCommandToServer("SELECT * FROM marks;");
        assertTrue(response.contains("[ERROR]"), "After dropping table, query should return an error. Response: " + response);
    }

    // Test DROP DATABASE: drop an existing database and verify that using it returns error.
    @Test
    public void testDropDatabase() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("DROP DATABASE " + randomName + ";");
        String response = sendCommandToServer("USE " + randomName + ";");
        assertTrue(response.contains("[ERROR]"), "After dropping database, USE should return error. Response: " + response);
    }

    // Additional complex tests:

    // Test for missing semicolon error.
    @Test
    public void testMissingSemicolon() {
        String randomName = generateRandomName();
        String response = sendCommandToServer("CREATE DATABASE " + randomName);
        assertTrue(response.contains("[ERROR]"), "Command missing semicolon should return error. Response: " + response);
    }

    // Test unsupported comparator error in SELECT (e.g., using '>' which is not supported in our implementation)
    @Test
    public void testUnsupportedComparator() {
        String randomName = generateRandomName();
        sendCommandToServer("CREATE DATABASE " + randomName + ";");
        sendCommandToServer("USE " + randomName + ";");
        sendCommandToServer("CREATE TABLE marks (name, mark, pass);");
        sendCommandToServer("INSERT INTO marks VALUES ('John', 70, TRUE);");
        String response = sendCommandToServer("SELECT * FROM marks WHERE mark > 60;");
        assertTrue(response.contains("[ERROR]"), "Using unsupported comparator '>' should return an error. Response: " + response);
    }
}
