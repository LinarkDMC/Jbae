package com.jbae.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Immutable description of a video game, as returned by IGDB or loaded from the local favorites database.
 *
 * @param id                   IGDB game id (used as primary key in the local database)
 * @param name                 game title
 * @param summary              short description (may be null)
 * @param releaseEpochSeconds  first release date as a Unix timestamp in seconds (may be null)
 * @param rating               aggregated rating from 0 to 100 (may be null)
 * @param genres               genre names
 * @param platforms            platform names
 * @param developers           developer company names
 * @param coverImageId         IGDB image id of the cover (may be null)
 * @param url                  IGDB web page of the game (may be null)
 */
public record Game(
        long id,
        String name,
        String summary,
        Long releaseEpochSeconds,
        Double rating,
        List<String> genres,
        List<String> platforms,
        List<String> developers,
        String coverImageId,
        String url) {

    /** Returns the release year/date as text, or "Unknown" when IGDB has no date. */
    public String releaseDateText() {
        if (releaseEpochSeconds == null) {
            return "Unknown";
        }
        LocalDate date = Instant.ofEpochSecond(releaseEpochSeconds).atZone(ZoneOffset.UTC).toLocalDate();
        return date.toString();
    }

    /** Returns the rating formatted as "87.5 / 100", or "N/A". */
    public String ratingText() {
        return rating == null ? "N/A" : String.format("%.1f / 100", rating);
    }

    /** Returns the cover URL for the given IGDB image size (e.g. "t_thumb", "t_cover_big"), or null. */
    public String coverUrl(String size) {
        if (coverImageId == null) {
            return null;
        }
        return "https://images.igdb.com/igdb/image/upload/" + size + "/" + coverImageId + ".jpg";
    }
}
