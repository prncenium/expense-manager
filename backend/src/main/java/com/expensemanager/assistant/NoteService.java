package com.expensemanager.assistant;

import com.expensemanager.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * What Muneem remembers across chats (ASSISTANT-SPEC §6). At most {@code app.assistant.max-notes}
 * notes of ≤ 200 chars; the oldest is dropped when a new one would go over. Read from the DB on
 * every request, never cached.
 */
@Service
public class NoteService {

    public static final int NOTE_MAX = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final int maxNotes;

    public NoteService(NamedParameterJdbcTemplate jdbc, @Value("${app.assistant.max-notes:30}") int maxNotes) {
        this.jdbc = jdbc;
        this.maxNotes = maxNotes;
    }

    public record Note(long id, String note, OffsetDateTime createdAt) {
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public List<Note> list() {
        return jdbc.query("SELECT id, note, created_at FROM assistant_notes ORDER BY id DESC",
                (rs, i) -> new Note(rs.getLong("id"), rs.getString("note"),
                        rs.getObject("created_at", OffsetDateTime.class)));
    }

    /**
     * Saves a note unless the same text (ignoring case and spaces) is already stored.
     * Returns false for a duplicate. Throws IllegalArgumentException for an empty or too long note.
     */
    @Transactional
    public boolean save(String note) {
        String text = note == null ? "" : note.trim().replaceAll("\s+", " ");
        if (text.isEmpty() || text.length() > NOTE_MAX) {
            throw new IllegalArgumentException("note must be 1-" + NOTE_MAX + " characters");
        }
        MapSqlParameterSource p = new MapSqlParameterSource("note", text).addValue("max", maxNotes);
        int inserted = jdbc.update("""
                INSERT INTO assistant_notes (note)
                SELECT :note
                WHERE NOT EXISTS (
                    SELECT 1 FROM assistant_notes
                    WHERE lower(regexp_replace(trim(note), '\s+', ' ', 'g')) = lower(:note))
                """, p);
        jdbc.update("""
                DELETE FROM assistant_notes
                WHERE id NOT IN (SELECT id FROM assistant_notes ORDER BY id DESC LIMIT :max)
                """, p);
        return inserted == 1;
    }

    @Transactional
    public void delete(long id) {
        if (jdbc.update("DELETE FROM assistant_notes WHERE id = :id", new MapSqlParameterSource("id", id)) == 0) {
            throw new NotFoundException("Note " + id + " not found");
        }
    }

    @Transactional
    public int deleteAll() {
        return jdbc.update("DELETE FROM assistant_notes", new MapSqlParameterSource());
    }
}
