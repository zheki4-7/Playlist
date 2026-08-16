package zhedron.playlist.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import zhedron.playlist.dto.SongDTO;
import zhedron.playlist.dto.response.PaginatedResponse;
import zhedron.playlist.entity.Playlist;
import zhedron.playlist.entity.Song;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Role;
import zhedron.playlist.enums.Status;
import zhedron.playlist.enums.Type;
import zhedron.playlist.exceptions.SongNotFoundException;
import zhedron.playlist.exceptions.UserNotEnoughPermissionsException;
import zhedron.playlist.mapper.SongMapper;
import zhedron.playlist.repository.PlaylistRepository;
import zhedron.playlist.repository.SongRepository;
import zhedron.playlist.services.impl.SongServiceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SongServiceTest {

    @Mock
    private SongRepository songRepository;

    @Mock
    private UserService userService;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private SongMapper songMapper;

    @InjectMocks
    private SongServiceImpl songService;

    @TempDir
    private Path tempDir;

    @Test
    void getSongByIdShouldReturnSong() {
        Song song = new Song();
        song.setId(1L);

        when(songRepository.findById(1L)).thenReturn(Optional.of(song));

        Song result = songService.getSongById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    void getSongByIdShouldThrowWhenSongMissing() {
        when(songRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(SongNotFoundException.class, () -> songService.getSongById(1L));
    }

    @Test
    void findAllPerWeekShouldReturnOnlyRecentSongs() {
        Song recentSong = new Song();
        recentSong.setId(1L);
        recentSong.setCreatedAt(LocalDateTime.now().minusDays(2));
        recentSong.setStatus(Status.PUBLISHED);

        Song oldSong = new Song();
        oldSong.setId(2L);
        oldSong.setCreatedAt(LocalDateTime.now().minusWeeks(2));

        when(songRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(recentSong, oldSong)));
        when(songMapper.songToSongDTO(recentSong))
                .thenReturn(new SongDTO(1L, "artist", "album", 5L, recentSong.getCreatedAt(), null, null, 120, Type.SINGLE, null, null, 1L, Status.PUBLISHED, null));

        PaginatedResponse response = songService.findAllPerWeek(0, 10);

        assertEquals(1, response.songs().size());
        assertEquals(1L, response.songs().get(0).id());
    }

    @Test
    void deleteSongByIdShouldDeleteSongForOwner() throws IOException {
        User owner = new User();
        owner.setId(5L);
        owner.setRole(Role.USER);

        Song song = new Song();
        song.setId(1L);
        song.setCreator(owner);
        song.setFileName("track.mp3");
        song.setImagePath("song.jpg");

        Path songFile = tempDir.resolve(song.getFileName());
        Path songImage = tempDir.resolve(song.getImagePath());

        Files.createFile(songFile);
        Files.createFile(songImage);

        assertTrue(Files.exists(songFile));
        assertTrue(Files.exists(songImage));

        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(userService.getCurrentUser()).thenReturn(owner);
        when(playlistRepository.findAllBySongsId(1L)).thenReturn(List.of(new Playlist()));

        songService.deleteSongById(1L);

        verify(playlistRepository).deleteAll(anyList());
        verify(songRepository).deleteById(1L);
    }

    @Test
    void deleteSongByIdShouldThrowWhenUserHasNoPermission() {
        User owner = new User();
        owner.setId(5L);

        User currentUser = new User();
        currentUser.setId(99L);
        currentUser.setRole(Role.USER);

        Song song = new Song();
        song.setId(1L);
        song.setCreator(owner);

        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(userService.getCurrentUser()).thenReturn(currentUser);

        UserNotEnoughPermissionsException exception = assertThrows(
                UserNotEnoughPermissionsException.class,
                () -> songService.deleteSongById(1L)
        );

        assertEquals("You do not have enough permissions to delete this song", exception.getMessage());
    }

    @Test
    void getTopSongsShouldReturnSongsSortedByViews() {
        Song first = new Song();
        first.setId(1L);
        first.setListeners(10L);
        first.setStatus(Status.PUBLISHED);

        Song second = new Song();
        second.setId(2L);
        second.setListeners(50L);
        second.setStatus(Status.PUBLISHED);

        Song third = new Song();
        third.setId(3L);
        third.setListeners(20L);
        third.setStatus(Status.PUBLISHED);

        when(songRepository.findAll()).thenReturn(List.of(first, second, third));
        when(songMapper.songToSongDTOList(anyList())).thenAnswer(invocation -> {
            List<Song> songs = invocation.getArgument(0);
            return songs.stream()
                    .map(song -> new SongDTO(song.getId(), null, null, song.getListeners(), null, null, null, 0, null, null, null, 0L, Status.PUBLISHED, null))
                    .toList();
        });

        List<SongDTO> result = songService.getTopSongs();

        assertEquals(2L, result.get(0).id());
        assertEquals(3L, result.get(1).id());
        assertEquals(1L, result.get(2).id());
    }

    @Test
    void findByArtistNameOrAlbumNameShouldReturnSongs() {
        Song song = new Song();
        song.setId(1L);
        song.setArtistName("artist");
        song.setAlbumName("album");
        song.setStatus(Status.PUBLISHED);

        SongDTO songDTO = new SongDTO(1L, "artist", "album", 0L, null, null, null, 0, null, null, null, 0L, Status.PUBLISHED, null);

        when(songRepository.findByArtistNameOrAlbumName("artist", "album")).thenReturn(List.of(song));
        when(songMapper.songToSongDTOList(List.of(song))).thenReturn(List.of(songDTO));

        List<SongDTO> result = songService.findByArtistNameOrAlbumName("artist", "album");

        assertEquals(1, result.size());
        assertEquals("artist", result.get(0).artistName());
        assertEquals("album", result.get(0).albumName());
    }

    @Test
    void findByArtistNameOrAlbumNameShouldThrowNotFoundWhenArtistNotFound() {
        when(songRepository.findByArtistNameOrAlbumName("artist", null)).thenReturn(List.of());

        SongNotFoundException exception = assertThrows(
                SongNotFoundException.class,
                () -> songService.findByArtistNameOrAlbumName("artist", null));

        assertEquals("Song not found with artist", exception.getMessage());
    }

    @Test
    void findByArtistNameOrAlbumNameShouldThrowNotFoundWhenAlbumNotFound() {
        when(songRepository.findByArtistNameOrAlbumName(null, "album")).thenReturn(List.of());

        SongNotFoundException exception = assertThrows(
                SongNotFoundException.class,
                () -> songService.findByArtistNameOrAlbumName(null, "album"));

        assertEquals("Song not found with album", exception.getMessage());
    }

    @Test
    void findByArtistNameOrAlbumNameShouldThrowNotFoundWhenBothNotFound() {
        when(songRepository.findByArtistNameOrAlbumName("artist", "album")).thenReturn(List.of());

        SongNotFoundException exception = assertThrows(
                SongNotFoundException.class,
                () -> songService.findByArtistNameOrAlbumName("artist", "album"));

        assertEquals("Song not found with artist and album", exception.getMessage());
    }

    @Test
    void getAllUploadsByUsersShouldReturnSongs() {
        User currentUser = new User();
        currentUser.setId(10L);

        Song song = new Song();
        song.setId(1L);
        song.setCreator(currentUser);

        Song song2 = new Song();
        song2.setId(2L);
        song2.setCreator(currentUser);

        List<Song> songs = List.of(song, song2);

        SongDTO songDTO1 = new SongDTO(1L, null, null, 0L, null, null, null, 0, null, null, null, 0L, null, null);
        SongDTO songDTO2 = new SongDTO(2L, null, null, 0L, null, null, null, 0, null, null, null, 0L, null, null);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(songRepository.findAllByCreator(currentUser)).thenReturn(songs);
        when(songMapper.songToSongDTOList(songs)).thenReturn(List.of(songDTO1, songDTO2));

        List<SongDTO> result = songService.findAllByMyUploads();

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals(2L, result.get(1).id());
    }
}
