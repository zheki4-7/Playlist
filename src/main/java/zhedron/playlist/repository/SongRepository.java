package zhedron.playlist.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zhedron.playlist.entity.Song;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Status;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {
    Page<Song> findAll(Pageable pageable);

    List<Song> findByArtistNameOrAlbumName(String artistName, String albumName);

    List<Song> findAllByCreator(User creator);

    List<Song> findAllByStatus(Status status);
}
