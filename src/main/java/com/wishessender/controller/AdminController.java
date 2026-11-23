package com.wishessender.controller;

import com.wishessender.util.Auth;
import com.wishessender.util.Db;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin panel: platform stats, users, gift catalog and festivals. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JdbcTemplate jdbc;

    public AdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(HttpSession session) {
        Auth.requireAdmin(session);
        Map<String, Object> data = new HashMap<>();
        data.put("users", jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        data.put("connections", jdbc.queryForObject("SELECT COUNT(*) FROM connections", Integer.class));
        data.put("events", jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class));
        data.put("wishes", jdbc.queryForObject("SELECT COUNT(*) FROM wishes", Integer.class));
        return data;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(HttpSession session) {
        Auth.requireAdmin(session);
        return jdbc.queryForList(
            "SELECT u.id, u.name, u.email, u.role, u.created_at, COUNT(c.id) AS connections " +
            "FROM users u LEFT JOIN connections c ON c.user_id = u.id " +
            "GROUP BY u.id, u.name, u.email, u.role, u.created_at ORDER BY u.id");
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable int id, HttpSession session) {
        Auth.requireAdmin(session);
        jdbc.update("DELETE FROM users WHERE id = ? AND role <> 'ADMIN'", id);
    }

    // ----- Gift catalog -----
    @GetMapping("/gifts")
    public List<Map<String, Object>> gifts(HttpSession session) {
        Auth.requireAdmin(session);
        return jdbc.queryForList("SELECT * FROM gifts ORDER BY id DESC");
    }

    @PostMapping("/gifts")
    public void addGift(@RequestBody Map<String, Object> b, HttpSession session) {
        Auth.requireAdmin(session);
        jdbc.update("INSERT INTO gifts (name, category, price, event_type, tag, reason) VALUES (?,?,?,?,?,?)",
            Db.required(b, "name"), Db.required(b, "category"), Db.num(b, "price", 0),
            b.getOrDefault("event_type", "ANY"), Db.required(b, "tag").toLowerCase(), Db.required(b, "reason"));
    }

    @DeleteMapping("/gifts/{id}")
    public void deleteGift(@PathVariable int id, HttpSession session) {
        Auth.requireAdmin(session);
        jdbc.update("DELETE FROM gifts WHERE id = ?", id);
    }

    // ----- Festivals -----
    @GetMapping("/festivals")
    public List<Map<String, Object>> festivals(HttpSession session) {
        Auth.requireAdmin(session);
        return jdbc.queryForList("SELECT * FROM festivals ORDER BY festival_date");
    }

    @PostMapping("/festivals")
    public void addFestival(@RequestBody Map<String, Object> b, HttpSession session) {
        Auth.requireAdmin(session);
        jdbc.update("INSERT INTO festivals (name, festival_date, religion, region) VALUES (?,?,?,?)",
            Db.required(b, "name"), Db.required(b, "festival_date"),
            b.getOrDefault("religion", "All"), b.getOrDefault("region", "Bangladesh"));
    }

    @DeleteMapping("/festivals/{id}")
    public void deleteFestival(@PathVariable int id, HttpSession session) {
        Auth.requireAdmin(session);
        jdbc.update("DELETE FROM festivals WHERE id = ?", id);
    }
}
