package zhedron.playlist.services.impl;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.AlbumDTO;
import zhedron.playlist.dto.request.AlbumRequest;
import zhedron.playlist.entity.Album;
import zhedron.playlist.entity.Song;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Type;
import zhedron.playlist.exceptions.AccessDeniedException;
import zhedron.playlist.exceptions.AlbumNotFoundException;
import zhedron.playlist.exceptions.SongNotFoundException;
import zhedron.playlist.mapper.AlbumMapper;
import zhedron.playlist.repository.AlbumRepository;
import zhedron.playlist.repository.SongRepository;
import zhedron.playlist.services.AlbumService;
import zhedron.playlist.services.UserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class AlbumServiceImpl implements AlbumService {
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;

    private final UserService userService;

    private final AlbumMapper albumMapper;

    private final String DIRECTORY = "art_album/";

    public AlbumServiceImpl(AlbumRepository albumRepository, SongRepository songRepository, UserService userService, AlbumMapper albumMapper) {
        this.albumRepository = albumRepository;
        this.songRepository = songRepository;
        this.userService = userService;
        this.albumMapper = albumMapper;
    }

    @Override
    @CachePut(value = "albums", key = "#result.id()")
    public AlbumDTO createAlbum(AlbumRequest albumRequest, MultipartFile image) throws IOException {
        Path path = Paths.get(DIRECTORY);

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }

        User user = userService.getCurrentUser();

        Album album = new Album();

        String fileName = UUID.randomUUID().toString() + "_" + image.getOriginalFilename();

        album.setContentType(image.getContentType());
        album.setCoverArtUrl(fileName);
        album.setTitle(albumRequest.getTitle());
        album.setCreator(user);

        Path imagePath = Paths.get(DIRECTORY).resolve(fileName).normalize();

        Files.copy(image.getInputStream(), imagePath, StandardCopyOption.REPLACE_EXISTING);

        Album savedAlbum = albumRepository.save(album);

        return albumMapper.albumToAlbumDTO(savedAlbum);
    }

    @Override
    @CacheEvict(value = "albums", key = "#id")
    public void deleteById(long id) {
        User user = userService.getCurrentUser();

        Album album = albumRepository.findById(id).orElseThrow(() -> new AlbumNotFoundException("Album not found with " + id));

        if (!album.getCreator().equals(user)) {
            throw new AccessDeniedException("You can't delete this album");
        }

        albumRepository.deleteById(id);
    }

    @Override
    public Album findById(long id) {
        return albumRepository.findById(id).orElseThrow(() -> new AlbumNotFoundException("Album not found with " + id));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "albums", key = "#albumId"),
            @CacheEvict(value = "users", key = "#result.creator().id()")
    })
    public AlbumDTO addSongToAlbum(long albumId, long songId) {
        Album album = albumRepository.findById(albumId).orElseThrow(() -> new AlbumNotFoundException("Album not found with " + albumId));

        Song foundSong = songRepository.findById(songId).orElseThrow(() -> new SongNotFoundException("Song not found with " + songId));

        User user = userService.getCurrentUser();

        if (!album.getCreator().equals(user) || !foundSong.getCreator().equals(user)) {
            throw new AccessDeniedException("You can't add this song to this album");
        }

        album.getSongs().add(foundSong);
        album.setDuration(album.getDuration() + foundSong.getDuration());

        if (album.getSongs().size() > 1) {
            for (Song song : album.getSongs()) {
                song.setType(Type.ALBUM);
            }
        }

        Album savedAlbum = albumRepository.save(album);

        return albumMapper.albumToAlbumDTO(savedAlbum);
    }
}
