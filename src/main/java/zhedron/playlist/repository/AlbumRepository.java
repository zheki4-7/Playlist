package zhedron.playlist.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zhedron.playlist.entity.Album;

public interface AlbumRepository extends JpaRepository<Album, Long> {
}
