
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = "DriverAPIController",
            urlPatterns = {"/api/v1/drivers/register"})
public class DriverAPIController extends HttpServlet {

    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        String fullName = request.getParameter("fullName");
        String phone = request.getParameter("phone");
        String email = request.getParameter("email");
        String address = request.getParameter("address");
        String licensePlate = request.getParameter("licensePlate");

        DriverDTO driver = new DriverDTO();
        driver.setFullName(fullName);
        driver.setPhone(phone);
        driver.setEmail(email);
        driver.setAddress(address);
        driver.setLicensePlate(licensePlate);

        DriverDAO dao = new DriverDAO();

        try {
            boolean success = dao.registerDriver(driver);

            if (success) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                response.getWriter().write("Driver registered successfully");
            }
        } catch (SQLException ex) {

            if (ex.getMessage().contains("UQ_Vehicle_Plate")) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write("License plate already exists");
            } else {
                throw new ServletException(ex);
            }
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }
}