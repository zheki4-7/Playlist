package zhedron.playlist.services;

import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.SongDTO;
import zhedron.playlist.dto.request.SongRequest;
import zhedron.playlist.dto.request.SongUpdateRequest;
import zhedron.playlist.dto.response.PaginatedResponse;
import zhedron.playlist.entity.Song;

import java.io.IOException;
import java.util.List;

public interface SongService {
    List<SongDTO> save(SongRequest requestSong, List<MultipartFile> files, MultipartFile image) throws IOException;

    Song getSongById(long id);

    void deleteSongById(long id);

    List<SongDTO> getTopSongs();

    PaginatedResponse findAllPerWeek(int page, int size);

    List<SongDTO> findByArtistNameOrAlbumName(String artistName, String albumName);

    List<SongDTO> findAllByMyUploads();

    SongDTO changeStatus(long id, SongUpdateRequest songUpdateRequest);
}
