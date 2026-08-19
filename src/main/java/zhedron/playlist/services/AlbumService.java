package zhedron.playlist.services;

import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.AlbumDTO;
import zhedron.playlist.dto.request.AlbumRequest;
import zhedron.playlist.entity.Album;

import java.io.IOException;

public interface AlbumService {
    AlbumDTO createAlbum(AlbumRequest albumRequest, MultipartFile image) throws IOException;

    void deleteById(long id);

    Album findById(long id);

    AlbumDTO addSongToAlbum(long songId, long albumId);
}
