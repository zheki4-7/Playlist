package zhedron.playlist.dto;

import java.util.List;

public record AlbumDTO(long id, long duration, List<SongDTO> songs,
                       String title, String coverArtUrl, String contentType) {
}
