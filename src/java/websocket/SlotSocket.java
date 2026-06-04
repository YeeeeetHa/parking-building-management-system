/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package websocket;

import utils.DbUtils;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
/**
 *
 * @author LENOVO
 */
@ServerEndpoint("/ws/slots")
public class SlotSocket {
    private static final Set<Session> clients = new CopyOnWriteArraySet<>();

    // khi client connect → gửi luôn grid hiện tại
    @OnOpen
    public void onOpen(Session session) {
        clients.add(session);
        try {
            session.getBasicRemote().sendText(getGridJson());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnClose
    public void onClose(Session session) {
        clients.remove(session);
    }

    // hàm lấy grid từ DB
    private static String getGridJson() {
        int[][] grid = new int[5][5];

        try (Connection conn = DbUtils.getConnection()) {

            String sql = "SELECT slot_code, status FROM Parking_slot";
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String slotCode = rs.getString("slot_code");
                String statusStr = rs.getString("status");

                // slot_code expected like 'A-01' or 'B-3'
                if (slotCode == null) continue;
                String[] parts = slotCode.split("-");
                if (parts.length < 2) continue;

                char rowChar = parts[0].trim().isEmpty() ? 'A' : parts[0].trim().charAt(0);
                int row = Character.toUpperCase(rowChar) - 'A';

                int col;
                try {
                    col = Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) - 1;
                } catch (NumberFormatException nfe) {
                    continue;
                }

                if (row < 0 || row >= grid.length || col < 0 || col >= grid[row].length) continue;

                int s = (statusStr != null && statusStr.equalsIgnoreCase("Occupied")) ? 1 : 0;
                grid[row][col] = s;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // convert JSON
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < grid.length; i++) {
            json.append("[");
            for (int j = 0; j < grid[i].length; j++) {
                json.append(grid[i][j]);
                if (j < grid[i].length - 1) json.append(",");
            }
            json.append("]");
            if (i < grid.length - 1) json.append(",");
        }
        json.append("]");

        return json.toString();
    }

    // gọi khi DB thay đổi
    public static void broadcastUpdate() {
        String data = getGridJson();

        for (Session s : clients) {
            try {
                s.getBasicRemote().sendText(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}