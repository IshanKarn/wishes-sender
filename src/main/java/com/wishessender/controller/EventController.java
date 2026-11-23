package com.wishessender.controller;

import com.wishessender.util.Auth;
import com.wishessender.util.Db;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/** Events of a connection (birthday, anniversary, festival, custom). */
@RestController
@RequestMapping("/api")
public class EventController {

    private final JdbcTemplate jdbc;

    public EventController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All events of one connection, with next date and days left (from the view). */
    @GetMapping("/connections/{connectionId}/events")
    public List<Map<String, Object>> list(@PathVariable int connectionId, HttpSession session) {
        return jdbc.queryForList(
            "SELECT * FROM v_event_next WHERE connection_id = ? AND user_id = ? ORDER BY days_left",
            connectionId, Auth.userId(session));
    }

    @PostMapping("/connections/{connectionId}/events")
    public Map<String, Object> create(@PathVariable int connectionId, @RequestBody Map<String, Object> b,
                                      HttpSession session) {
        // Make sure the connection belongs to the logged-in user
        Db.one(jdbc, "SELECT id FROM connections WHERE id = ? AND user_id = ?", connectionId, Auth.userId(session));
        int id = Db.insert(jdbc,
            "INSERT INTO events (connection_id, title, event_type, event_date, yearly, description) VALUES (?,?,?,?,?,?)",
            connectionId, Db.required(b, "title"), Db.required(b, "event_type"), Db.required(b, "event_date"),
            Db.num(b, "yearly", 1), Db.str(b, "description"));
        return Db.one(jdbc, "SELECT * FROM v_event_next WHERE id = ?", id);
    }

    @DeleteMapping("/events/{id}")
    public void delete(@PathVariable int id, HttpSession session) {
        jdbc.update("DELETE e FROM events e JOIN connections c ON c.id = e.connection_id " +
                    "WHERE e.id = ? AND c.user_id = ?", id, Auth.userId(session));
    }

    /** Events in the next N days, filtered by type (Search & Filter / Calendar list). */
    @GetMapping("/events/upcoming")
    public List<Map<String, Object>> upcoming(@RequestParam(defaultValue = "30") int days,
                                              @RequestParam(defaultValue = "") String type,
                                              HttpSession session) {
        return jdbc.queryForList(
            "SELECT * FROM v_event_next WHERE user_id = ? AND days_left BETWEEN 0 AND ? " +
            "AND (? = '' OR event_type = ?) ORDER BY days_left",
            Auth.userId(session), days, type, type);
    }

    /** Festival master list. With ?religion=Islam it shows festivals for that religion + 'All'. */
    @GetMapping("/festivals")
    public List<Map<String, Object>> festivals(@RequestParam(defaultValue = "") String religion) {
        return jdbc.queryForList(
            "SELECT * FROM festivals WHERE festival_date >= CURDATE() " +
            "AND (? = '' OR religion = 'All' OR religion = ?) ORDER BY festival_date",
            religion, religion);
    }
}
