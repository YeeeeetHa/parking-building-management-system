
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import utils.DbUtils;

public class DriverDAO {

    public boolean registerDriver(DriverDTO driver) throws Exception {

        Connection con = DbUtils.getConnection();

        String sql
                = "INSERT INTO Driver(full_name, phone, email, address, license_plate) "
                + "VALUES(?,?,?,?,?)";

        PreparedStatement ps = con.prepareStatement(sql);

        ps.setString(1, driver.getFullName());
        ps.setString(2, driver.getPhone());
        ps.setString(3, driver.getEmail());
        ps.setString(4, driver.getAddress());
        ps.setString(5, driver.getLicensePlate());

        return ps.executeUpdate() > 0;
    }
}
