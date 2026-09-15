package com.letter.repository;

import java.util.List;
import java.util.Optional;
import java.sql.Statement;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import com.letter.model.Letter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LetterRepository {
    private final JdbcTemplate jdbcTemplate;

    public LetterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Letter> MAPPER = (rs, rowNum) -> new Letter(
        rs.getLong("id"), rs.getString("content"), rs.getString("status"),
        rs.getObject("created_at", java.time.LocalDateTime.class),
        rs.getObject("updated_at", java.time.LocalDateTime.class),
        rs.getObject("sent_at", java.time.LocalDateTime.class)
    );
    private static final String SELECT =
        "SELECT id, content, status, created_at, updated_at, sent_at FROM letters";

    public long saveDraft(String content) {
        var keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO letters (content, status, updated_at) VALUES (?, 'DRAFT', CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, content);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("保存后未取得信件编号");
        }
        return id.longValue();
    }

    public List<Letter> findAll() {
        return jdbcTemplate.query(SELECT + " ORDER BY id DESC", MAPPER);
    }

    public int updateDraft(long id, String content) {
        return jdbcTemplate.update(
            "UPDATE letters SET content = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'DRAFT'",
            content, id);
    }

    public Optional<Letter> findByIdForUpdate(long id) {
        return jdbcTemplate.query(SELECT + " WHERE id = ? FOR UPDATE", MAPPER, id).stream().findFirst();
    }

    public int sendDraft(long id) {
        return jdbcTemplate.update(
            "UPDATE letters SET status = 'SENT', sent_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'DRAFT'",
            id);
    }

    public Optional<Letter> findById(long id) {
        return jdbcTemplate.query(SELECT + " WHERE id = ?", MAPPER, id).stream().findFirst();
    }
}
