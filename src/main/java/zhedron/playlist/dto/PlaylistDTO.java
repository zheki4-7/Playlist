package zhedron.playlist.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record PlaylistDTO(long id, Set<SongDTO> songs, long views,
                          long duration, boolean isPublic, int counter,
                          LocalDateTime createdAt, String imageURL, String contentType,
                          String title) {
}
