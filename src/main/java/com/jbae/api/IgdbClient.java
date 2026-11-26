package com.jbae.api;

import com.jbae.model.Game;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal client for the IGDB v4 API.
 *
 * <p>IGDB uses Twitch authentication: the client id and secret are exchanged for an
 * app access token (client credentials flow), which is then sent with every request.
 * The token is cached and renewed automatically when it expires.
 */
public class IgdbClient {

    private static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    private static final String GAMES_URL = "https://api.igdb.com/v4/games";

    /** Fields requested for every game (Apicalypse query syntax). */
    private static final String FIELDS = "fields name,summary,storyline,first_release_date,total_rating,"
            + "genres.name,platforms.name,cover.image_id,url,"
            + "involved_companies.developer,involved_companies.company.name;";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String clientId;
    private final String clientSecret;

    private String accessToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public IgdbClient(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Lists well-known games, sorted by how many users rated them. */
    public List<Game> browsePopular(int limit, int offset) throws IOException, InterruptedException {
        String body = FIELDS
                + " where total_rating_count > 20 & cover != null & version_parent = null;"
                + " sort total_rating_count desc;"
                + " limit " + limit + "; offset " + offset + ";";
        return queryGames(body);
    }

    /** Searches games by title. Note: IGDB does not allow "sort" together with "search". */
    public List<Game> search(String text, int limit, int offset) throws IOException, InterruptedException {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        String body = "search \"" + escaped + "\"; " + FIELDS
                + " where version_parent = null;"
                + " limit " + limit + "; offset " + offset + ";";
        return queryGames(body);
    }

    // ------------------------------------------------------------------ HTTP

    private List<Game> queryGames(String apicalypseBody) throws IOException, InterruptedException {
        HttpResponse<String> response = post(apicalypseBody);
        if (response.statusCode() == 401) {
            // The token may have been revoked: discard it and retry once with a fresh one.
            synchronized (this) {
                accessToken = null;
            }
            response = post(apicalypseBody);
        }
        if (response.statusCode() != 200) {
            throw new IOException("IGDB returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return parseGames(response.body());
    }

    private HttpResponse<String> post(String apicalypseBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GAMES_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Client-ID", clientId)
                .header("Authorization", "Bearer " + getAccessToken())
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(apicalypseBody))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private synchronized String getAccessToken() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(tokenExpiry)) {
            return accessToken;
        }
        String form = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&grant_type=client_credentials";
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Twitch authentication failed (HTTP " + response.statusCode()
                    + "). Check your client id and secret. " + response.body());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        accessToken = json.get("access_token").getAsString();
        long expiresIn = json.get("expires_in").getAsLong();
        // Renew one minute early to avoid using a token that is about to expire.
        tokenExpiry = Instant.now().plusSeconds(Math.max(0, expiresIn - 60));
        return accessToken;
    }

    // --------------------------------------------------------------- parsing

    private static List<Game> parseGames(String json) {
        List<Game> games = new ArrayList<>();
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement element : array) {
            games.add(parseGame(element.getAsJsonObject()));
        }
        return games;
    }

    private static Game parseGame(JsonObject o) {
        String coverImageId = null;
        if (hasValue(o, "cover") && o.get("cover").isJsonObject()) {
            coverImageId = string(o.getAsJsonObject("cover"), "image_id");
        }

        return new Game(
                o.get("id").getAsLong(),
                string(o, "name"),
                string(o, "summary"),
                hasValue(o, "first_release_date") ? o.get("first_release_date").getAsLong() : null,
                hasValue(o, "total_rating") ? o.get("total_rating").getAsDouble() : null,
                namesOf(o, "genres"),
                namesOf(o, "platforms"),
                developersOf(o),
                coverImageId,
                string(o, "url"));
    }

    /** Collects the "name" field of every object in an expanded array such as genres or platforms. */
    private static List<String> namesOf(JsonObject o, String field) {
        List<String> names = new ArrayList<>();
        if (hasValue(o, field) && o.get(field).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(field)) {
                if (e.isJsonObject()) {
                    String name = string(e.getAsJsonObject(), "name");
                    if (name != null) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }

    /** Collects the names of the companies flagged as developers. */
    private static List<String> developersOf(JsonObject o) {
        List<String> developers = new ArrayList<>();
        if (hasValue(o, "involved_companies") && o.get("involved_companies").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("involved_companies")) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject involved = e.getAsJsonObject();
                boolean isDeveloper = hasValue(involved, "developer") && involved.get("developer").getAsBoolean();
                if (isDeveloper && hasValue(involved, "company") && involved.get("company").isJsonObject()) {
                    String name = string(involved.getAsJsonObject("company"), "name");
                    if (name != null) {
                        developers.add(name);
                    }
                }
            }
        }
        return developers;
    }

    private static boolean hasValue(JsonObject o, String field) {
        return o.has(field) && !o.get(field).isJsonNull();
    }

    private static String string(JsonObject o, String field) {
        return hasValue(o, field) ? o.get(field).getAsString() : null;
    }
}
