package zhedron.playlist.dto;

import zhedron.playlist.enums.Status;
import zhedron.playlist.enums.Type;

import java.time.LocalDateTime;

public record SongDTO(long id, String artistName, String albumName,
                      long listeners, LocalDateTime createdAt, String contentType,
                      String fileName, int duration, Type type,
                      String imagePath, String contentTypeImage, long creatorId,
                      Status status, LocalDateTime publishedAt) {
}
