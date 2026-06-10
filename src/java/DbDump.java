import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;

public class DbDump {
    public static void main(String[] args) {
        String DB_USER = "SWP391_Admin";
        String DB_PASSWORD = "SWP@391DB"; 
        String URL = "jdbc:sqlserver://swp391db.database.windows.net:1433;databaseName=" 
        + "SmartParkingDB" + ";encrypt=true;trustServerCertificate=true;";
        
        try {
            Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, "%", new String[] {"TABLE"});
            System.out.println("--- TABLES ---");
            while (rs.next()) {
                System.out.println(rs.getString("TABLE_NAME"));
            }
            rs.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
