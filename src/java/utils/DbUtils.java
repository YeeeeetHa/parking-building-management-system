/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbUtils {
    
    // Maps perfectly to your script line: USE SmartParkingDB;
    private static final String DB_NAME = "SmartParkingDB"; 
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "12345"; 
    
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=" + DB_NAME 
                                    + ";encrypt=true;trustServerCertificate=true;";

    public static Connection getConnection() throws ClassNotFoundException, SQLException {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
    }
}