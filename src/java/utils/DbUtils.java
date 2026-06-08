/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/*
 * DbUtils — single shared helper for getting a JDBC connection to SQL Server
 *
 * Every DAO in the project calls DbUtils.getConnection() to talk to the DB.
 * The connection is not pooled — each call opens a new connection.
 * In production you'd want a connection pool (e.g. HikariCP), but for this sprint it's fine.
 */
public class DbUtils {
    
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "12345"; 
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=" 
    + "SmartParkingDB" + ";encrypt=true;trustServerCertificate=true;";

    // Returns a fresh DB connection
    public static Connection getConnection() throws ClassNotFoundException, SQLException {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
    }
}
