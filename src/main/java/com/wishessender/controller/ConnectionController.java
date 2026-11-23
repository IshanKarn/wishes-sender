package com.wishessender.controller;

import com.wishessender.util.Auth;
import com.wishessender.util.Db;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/** Add, list/search, view, update and delete connections (people). */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final JdbcTemplate jdbc;

    public ConnectionController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** List with optional search text (q) and relation filter. Also returns days to the next event. */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "") String q,
                                          @RequestParam(defaultValue = "") String relation,
                                          HttpSession session) {
        int userId = Auth.userId(session);
        String sql =
            "SELECT c.*, " +
            "  (SELECT MIN(v.days_left) FROM v_event_next v " +
            "   WHERE v.connection_id = c.id AND v.days_left >= 0) AS next_in_days " +
            "FROM connections c " +
            "WHERE c.user_id = ? AND c.name LIKE ? AND (? = '' OR c.relation = ?) " +
            "ORDER BY c.name";
        return jdbc.queryForList(sql, userId, "%" + q + "%", relation, relation);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable int id, HttpSession session) {
        return Db.one(jdbc, "SELECT * FROM connections WHERE id = ? AND user_id = ?", id, Auth.userId(session));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> b, HttpSession session) {
        int userId = Auth.userId(session);
        int id = Db.insert(jdbc,
            "INSERT INTO connections (user_id, name, relation, phone, email, address, dob, gender, religion, " +
            "favourite_dish, interests, gift_min, gift_max, notes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            userId, Db.required(b, "name"), Db.required(b, "relation"), Db.str(b, "phone"), Db.str(b, "email"),
            Db.str(b, "address"), Db.str(b, "dob"), Db.str(b, "gender"), Db.str(b, "religion"),
            Db.str(b, "favourite_dish"), Db.str(b, "interests"),
            Db.num(b, "gift_min", 0), Db.num(b, "gift_max", 5000), Db.str(b, "notes"));

        // If a date of birth is given, create the birthday event automatically
        if (Db.str(b, "dob") != null) {
            jdbc.update("INSERT INTO events (connection_id, title, event_type, event_date, yearly) " +
                        "VALUES (?, 'Birthday', 'BIRTHDAY', ?, 1)", id, Db.str(b, "dob"));
        }
        return get(id, session);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable int id, @RequestBody Map<String, Object> b, HttpSession session) {
        get(id, session); // 404 if it is not this user's connection
        jdbc.update(
            "UPDATE connections SET name=?, relation=?, phone=?, email=?, address=?, dob=?, gender=?, religion=?, " +
            "favourite_dish=?, interests=?, gift_min=?, gift_max=?, notes=? WHERE id=?",
            Db.required(b, "name"), Db.required(b, "relation"), Db.str(b, "phone"), Db.str(b, "email"),
            Db.str(b, "address"), Db.str(b, "dob"), Db.str(b, "gender"), Db.str(b, "religion"),
            Db.str(b, "favourite_dish"), Db.str(b, "interests"),
            Db.num(b, "gift_min", 0), Db.num(b, "gift_max", 5000), Db.str(b, "notes"), id);
        return get(id, session);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id, HttpSession session) {
        // Events, wishes and notifications are removed too (ON DELETE CASCADE)
        jdbc.update("DELETE FROM connections WHERE id = ? AND user_id = ?", id, Auth.userId(session));
    }
}
