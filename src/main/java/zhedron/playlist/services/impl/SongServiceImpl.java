package zhedron.playlist.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.SongDTO;
import zhedron.playlist.dto.request.SongRequest;
import zhedron.playlist.dto.request.SongUpdateRequest;
import zhedron.playlist.dto.response.PaginatedResponse;
import zhedron.playlist.entity.Playlist;
import zhedron.playlist.entity.Song;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Role;
import zhedron.playlist.enums.Status;
import zhedron.playlist.enums.Type;
import zhedron.playlist.exceptions.AccessDeniedException;
import zhedron.playlist.exceptions.SongNotFoundException;
import zhedron.playlist.exceptions.UserNotEnoughPermissionsException;
import zhedron.playlist.mapper.SongMapper;
import zhedron.playlist.repository.PlaylistRepository;
import zhedron.playlist.repository.SongRepository;
import zhedron.playlist.services.SongService;
import zhedron.playlist.services.UserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SongServiceImpl implements SongService {
    private final SongRepository songRepository;
    private final UserService userService;
    private final PlaylistRepository playlistRepository;
    private final SongMapper songMapper;

    private final String FILEPATH = "song/";

    private final String FILEPATH_IMAGE = "song/image/";

    public SongServiceImpl(SongRepository songRepository, UserService userService, PlaylistRepository playlistRepository, SongMapper songMapper) {
        this.songRepository = songRepository;
        this.userService = userService;
        this.playlistRepository = playlistRepository;
        this.songMapper = songMapper;
    }

    @Override
    public List<SongDTO> save(SongRequest requestSong, List<MultipartFile> files, MultipartFile image) throws IOException {
        Path path = Paths.get(FILEPATH);
        Path imagePath = Paths.get(FILEPATH_IMAGE);

        List<Song> songList = new ArrayList<>();

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }

        if (Files.notExists(imagePath)) {
            Files.createDirectories(imagePath);
        }

        User currentUser = userService.getCurrentUser();

        System.out.println(currentUser.getId());

        for (MultipartFile multipartFile : files) {

            Type type = files.size() > 1 ? Type.ALBUM : Type.SINGLE;

            String songName = currentUser.getId() + "_" + requestSong.getArtistName() + "_" + requestSong.getAlbumName() + "_" + multipartFile.getOriginalFilename();

            Path createFile = Paths.get(FILEPATH).resolve(songName).normalize();

            Files.copy(multipartFile.getInputStream(), createFile, StandardCopyOption.REPLACE_EXISTING);

            String imageName = currentUser.getId() + "_" + requestSong.getAlbumName() + "_" + image.getOriginalFilename();

            Path createImage = Paths.get(FILEPATH_IMAGE).resolve(imageName).normalize();

            Files.copy(image.getInputStream(), createImage, StandardCopyOption.REPLACE_EXISTING);

            Song song = new Song();

            song.setCreator(currentUser);
            song.setContentType(multipartFile.getContentType());
            song.setContentTypeImage(image.getContentType());
            song.setFileName(songName);
            song.setImagePath(imageName);
            song.setCreatedAt(LocalDateTime.now());
            song.setAlbumName(requestSong.getAlbumName());
            song.setArtistName(requestSong.getArtistName());
            song.setType(type);
            song.setStatus(Status.ARCHIVED);

            songList.add(song);

            log.info("Saved song: {}", song);

            try {
                AudioFile audioFile = AudioFileIO.read(createFile.toFile());
                AudioHeader audioHeader = audioFile.getAudioHeader();

                song.setDuration(audioHeader.getTrackLength());
            } catch (Exception e) {
                log.error("Audio file error {}", e.getMessage());
            }
        }

        List<Song> savedSong = songRepository.saveAll(songList);

        System.out.println(savedSong.get(0).getCreator().getId());

        return songMapper.songToSongDTOList(savedSong);
    }

    @Override
    @Cacheable(value = "songs", key = "#id")
    public Song getSongById(long id) {
        return songRepository.findById(id).orElseThrow(() -> new SongNotFoundException("Song not found with " + id));
    }

    @Override
    @CacheEvict(value = "songs", key = "#id")
    public void deleteSongById(long id) {
        Song song = songRepository.findById(id).orElseThrow(() -> new SongNotFoundException("Song not found with " + id));

        User currentUser = userService.getCurrentUser();

        if (song.getCreator().getId() != currentUser.getId() && !currentUser.getRole().equals(Role.ADMIN)) {
            throw new UserNotEnoughPermissionsException("You do not have enough permissions to delete this song");
        }

        List<Playlist> playlists = playlistRepository.findAllBySongsId(song.getId());

        playlistRepository.deleteAll(playlists);

        songRepository.deleteById(id);

        Path songPath = Paths.get(FILEPATH).resolve(song.getFileName()).normalize();

        Path imagePath = Paths.get(FILEPATH_IMAGE).resolve(song.getImagePath()).normalize();

        try {
            Files.deleteIfExists(songPath);
        } catch (IOException e) {
            log.error("Failed to delete song: {}", e.getMessage());
        }

        try {
            Files.deleteIfExists(imagePath);
        } catch (IOException e) {
            log.error("Failed to delete image: {}", e.getMessage());
        }
    }

    @Override
    public List<SongDTO> getTopSongs() {
        List<Song> songs = songRepository.findAll();

        List<Song> topSongs = songs.stream().filter(song -> song.getStatus().equals(Status.PUBLISHED))
                .sorted(Comparator.comparingLong(Song::getListeners)
                        .reversed())
                .limit(10).
                collect(Collectors.toList());

        return songMapper.songToSongDTOList(topSongs);
    }

    @Override
    public PaginatedResponse findAllPerWeek(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("listeners").descending());

        Page<Song> songPage = songRepository.findAll(pageable);

        List<Song> findAllPerWeek = songPage.getContent().stream().filter(s -> s.getCreatedAt().isAfter(LocalDateTime.now().minusWeeks(1)) && s.getStatus().equals(Status.PUBLISHED)).collect(Collectors.toList());

        List<SongDTO> songResponses = findAllPerWeek.stream().map(songMapper::songToSongDTO).collect(Collectors.toList());

        return new PaginatedResponse(
                songResponses,
                songPage.getNumber(),
                songPage.getSize(),
                songPage.getTotalElements(),
                songPage.getTotalPages(),
                songPage.isLast(),
                songPage.isFirst(),
                songPage.hasNext(),
                songPage.hasPrevious()
        );
    }

    @Override
    public List<SongDTO> findByArtistNameOrAlbumName(String artistName, String albumName) {
        List<Song> songs = songRepository.findByArtistNameOrAlbumName(artistName, albumName);

        List<Song> songsPublished = songs.stream().filter(song -> song.getStatus().equals(Status.PUBLISHED)).collect(Collectors.toList());

        if (songsPublished.isEmpty()) {
            if (artistName != null && albumName == null) {
                throw new SongNotFoundException("Song not found with " + artistName);
            } else if (artistName == null && albumName != null) {
                throw new SongNotFoundException("Song not found with " + albumName);
            } else {
                throw new SongNotFoundException("Song not found with " + artistName + " and " + albumName);
            }
        }

        return songMapper.songToSongDTOList(songsPublished);
    }

    @Override
    public List<SongDTO> findAllByMyUploads() {
        User currentUser = userService.getCurrentUser();

        List<Song> songs = songRepository.findAllByCreator(currentUser);

        return songMapper.songToSongDTOList(songs);
    }

    @Override
    @CacheEvict(value = "songs", key = "#id")
    public SongDTO changeStatus(long id, SongUpdateRequest songUpdateRequest) {
        Song song = songRepository.findById(id).orElseThrow(() -> new SongNotFoundException("Song not found with " + id));

        User currentUser = userService.getCurrentUser();

        if (!song.getCreator().equals(currentUser)) {
            throw new AccessDeniedException("You don't have permissions to change status this song");
        }

        song.setStatus(songUpdateRequest.getStatus());
        song.setPublishedAt(songUpdateRequest.getPublishedAt());

        Song updatedSong = songRepository.save(song);

        return songMapper.songToSongDTO(updatedSong);
    }
}
