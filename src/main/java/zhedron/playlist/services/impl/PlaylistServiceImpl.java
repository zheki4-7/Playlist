package zhedron.playlist.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.PlaylistDTO;
import zhedron.playlist.dto.request.PlaylistRequest;
import zhedron.playlist.entity.Playlist;
import zhedron.playlist.entity.Song;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Role;
import zhedron.playlist.exceptions.AccessDeniedException;
import zhedron.playlist.exceptions.PlaylistNotFoundException;
import zhedron.playlist.exceptions.UserNotFoundException;
import zhedron.playlist.mapper.PlaylistMapper;
import zhedron.playlist.mapper.SongMapper;
import zhedron.playlist.repository.PlaylistRepository;
import zhedron.playlist.repository.UserRepository;
import zhedron.playlist.services.PlaylistService;
import zhedron.playlist.services.SongService;
import zhedron.playlist.services.UserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class PlaylistServiceImpl implements PlaylistService {
    private final PlaylistRepository playlistRepository;
    private final UserRepository userRepository;
    private final SongService songService;
    private final UserService userService;
    private final PlaylistMapper playlistMapper;

    private final String PATH = "playlist_image";
    private final SongMapper songMapper;

    public PlaylistServiceImpl(PlaylistRepository playlistRepository, UserRepository userRepository, SongService songService, UserService userService, PlaylistMapper playlistMapper, SongMapper songMapper) {
        this.playlistRepository = playlistRepository;
        this.userRepository = userRepository;
        this.songService = songService;
        this.userService = userService;
        this.playlistMapper = playlistMapper;
        this.songMapper = songMapper;
    }

    @Override
    public void addSong(long idSong, long playlistId) {
        Song song = songService.getSongById(idSong);

        User user = userService.getCurrentUser();

        Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));

        if (!playlist.getUser().equals(user)) {
            throw new AccessDeniedException("You're can't add this song to this playlist");
        }

        playlist.getSongs().add(song);
        playlist.setUser(user);
        playlist.setDuration(playlist.getDuration() + song.getDuration());
        playlist.setCreatedAt(LocalDateTime.now());

        playlist.setCounter(playlist.getSongs().size());

        playlistRepository.save(playlist);

        user.getPlaylists().add(playlist);

        userRepository.save(user);
    }

    @Override
    public List<PlaylistDTO> getPlaylistsByArtistNameOrAlbumName(String artistName, String albumName, long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found with " + userId);
        }

        if (!playlistRepository.existsPlaylistByArtistNameOrAlbumNameAndUserId(artistName, albumName, userId)) {
            throw new PlaylistNotFoundException("Playlist not found with " + artistName + " " + albumName);
        }

        List<Playlist> playlists = playlistRepository.findByArtistNameOrAlbumNameAndUserId(artistName, albumName, userId);

        return playlistMapper.toPlaylistDTO(playlists);

    }

    @Override
    @Cacheable(value = "playlists", key = "#playlistId")
    public void changeVisibility(long playlistId, boolean isPublic) {
        Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() -> new PlaylistNotFoundException("Playlist not found with " + playlistId));

        User currentUser = userService.getCurrentUser();

        if (!playlist.getUser().equals(currentUser)) {
            throw new AccessDeniedException("You're can't change this playlist");
        } else if (playlist.isPublic() == isPublic) {
            return;
        }

        playlist.setPublic(isPublic);

        playlistRepository.save(playlist);
    }

    @Override
    public void savePlaylist(PlaylistRequest playlistRequest, MultipartFile file) throws IOException {
        Path path = Paths.get(PATH);

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }

        User user = userService.getCurrentUser();

        String fileName = user.getName() + " " + file.getOriginalFilename();

        String contentType = file.getContentType();

        Path createFile = Paths.get(PATH).resolve(fileName).normalize();

        Files.copy(file.getInputStream(), createFile, StandardCopyOption.REPLACE_EXISTING);

        Playlist playlist = new Playlist();

        playlist.setTitle(playlistRequest.getTitle());
        playlist.setUser(user);
        playlist.setPublic(playlistRequest.isPublic());
        playlist.setImageURL(fileName);
        playlist.setContentType(contentType);
        playlist.setCreatedAt(LocalDateTime.now());

        user.getPlaylists().add(playlist);

        playlistRepository.save(playlist);

        userRepository.save(user);
    }

    @Override
    @CacheEvict(value = "playlists", key = "#playlistId")
    public void deletePlaylist(long playlistId) {
        User currentUser = userService.getCurrentUser();

        Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() -> new PlaylistNotFoundException("Playlist not found with " + playlistId));

        if (!playlist.getUser().equals(currentUser)) {
            throw new AccessDeniedException("You can't delete this playlist");
        }

        playlistRepository.delete(playlist);

        log.info("Playlist deleted successfully {}", playlistId);
    }

    @Override
    @Cacheable(value = "playlists", key = "#playlistId")
    public PlaylistDTO findPlaylistById(long playlistId) {
        Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() -> new PlaylistNotFoundException("Playlist not found with " + playlistId));

        if (!playlist.isPublic()) {
            throw new AccessDeniedException("You're can't find this playlist");
        }

        return playlistMapper.toPlaylistDTO(playlist);
    }

    @Override
    @Caching(cacheable = {
            @Cacheable(value = "users", key = "#userId"),
            @Cacheable(value = "playlists", key = "#playlistId")
    })
    public PlaylistDTO getPlaylist(long playlistId, long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found with " + userId));

        User currentUser = userService.getCurrentUser();

        Playlist playlist = playlistRepository.findById(playlistId).orElseThrow(() -> new PlaylistNotFoundException("Playlist not found with " + playlistId));

        checkAccess(currentUser, playlist, user, playlistId);

        return playlistMapper.toPlaylistDTO(playlist);
    }

    private void checkAccess(User currentUser, Playlist playlist, User user, long playlistId) {
        if (!user.getPlaylists().contains(playlist)) {
            throw new PlaylistNotFoundException("Playlist not found with " + playlistId);
        }

        boolean isOwner = playlist.getUser().equals(currentUser);
        boolean isAdmin = currentUser.getRole().equals(Role.ADMIN);

        if (!playlist.isPublic() && !isOwner && !isAdmin) {
            throw new AccessDeniedException("You're can't get this playlist");
        }
    }
}