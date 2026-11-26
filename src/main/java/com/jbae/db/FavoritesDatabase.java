package com.jbae.db;

import com.jbae.model.Game;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores the user's favorite games in a local SQLite file so they survive between sessions.
 *
 * <p>List-like fields (genres, platforms, developers) are stored as JSON text in a single column.
 * All public methods are synchronized because the UI may call them from different threads.
 */
public class FavoritesDatabase implements AutoCloseable {

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST = new TypeToken<List<String>>() { }.getType();

    private final Connection connection;

    /** Opens (and creates, if needed) the database file at the given location. */
    public FavoritesDatabase(Path file) throws SQLException, IOException {
        Path absolute = file.toAbsolutePath();
        if (absolute.getParent() != null) {
            Files.createDirectories(absolute.getParent());
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
        createSchema();
    }

    /** Default location: ~/.jbae/favorites.db */
    public static Path defaultLocation() {
        return Path.of(System.getProperty("user.home"), ".jbae", "favorites.db");
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS favorites (
                        id              INTEGER PRIMARY KEY,
                        name            TEXT NOT NULL,
                        summary         TEXT,
                        release_date    INTEGER,
                        rating          REAL,
                        genres          TEXT NOT NULL,
                        platforms       TEXT NOT NULL,
                        developers      TEXT NOT NULL,
                        cover_image_id  TEXT,
                        url             TEXT,
                        added_at        TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    /** Adds a game to the favorites (or refreshes its stored data if it is already there). */
    public synchronized void add(Game game) throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO favorites
                    (id, name, summary, release_date, rating, genres, platforms, developers, cover_image_id, url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, game.id());
            ps.setString(2, game.name());
            ps.setString(3, game.summary());
            ps.setObject(4, game.releaseEpochSeconds());
            ps.setObject(5, game.rating());
            ps.setString(6, GSON.toJson(game.genres()));
            ps.setString(7, GSON.toJson(game.platforms()));
            ps.setString(8, GSON.toJson(game.developers()));
            ps.setString(9, game.coverImageId());
            ps.setString(10, game.url());
            ps.executeUpdate();
        }
    }

    /** Removes a game from the favorites. Does nothing if it was not a favorite. */
    public synchronized void remove(long gameId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM favorites WHERE id = ?")) {
            ps.setLong(1, gameId);
            ps.executeUpdate();
        }
    }

    /** Returns every favorite game, most recently added first. */
    public synchronized List<Game> findAll() throws SQLException {
        List<Game> games = new ArrayList<>();
        String sql = "SELECT * FROM favorites ORDER BY added_at DESC, id DESC";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                games.add(readGame(rs));
            }
        }
        return games;
    }

    /** Returns only the ids of the favorites; handy for marking rows in the UI. */
    public synchronized Set<Long> findAllIds() throws SQLException {
        Set<Long> ids = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id FROM favorites")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static Game readGame(ResultSet rs) throws SQLException {
        long releaseDate = rs.getLong("release_date");
        Long release = rs.wasNull() ? null : releaseDate;
        double rating = rs.getDouble("rating");
        Double ratingValue = rs.wasNull() ? null : rating;

        return new Game(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("summary"),
                release,
                ratingValue,
                GSON.fromJson(rs.getString("genres"), STRING_LIST),
                GSON.fromJson(rs.getString("platforms"), STRING_LIST),
                GSON.fromJson(rs.getString("developers"), STRING_LIST),
                rs.getString("cover_image_id"),
                rs.getString("url"));
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }
}
