package zhedron.playlist.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zhedron.playlist.dto.PlaylistDTO;
import zhedron.playlist.dto.SongDTO;
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
import zhedron.playlist.services.impl.PlaylistServiceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @InjectMocks
    private PlaylistServiceImpl playlistService;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SongService songService;

    @Mock
    private UserService userService;

    @Mock
    private PlaylistMapper playlistMapper;

    @Mock
    private SongMapper songMapper;

    @Test
    void addSongShouldAddSongToExistingPlaylist() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setPlaylists(new ArrayList<>());

        Song song = new Song();
        song.setId(5L);
        song.setDuration(180);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setUser(user);
        playlist.setDuration(20L);

        when(songService.getSongById(5L)).thenReturn(song);
        when(userService.getCurrentUser()).thenReturn(user);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        playlistService.addSong(5L, 10L);

        assertEquals(1, user.getPlaylists().size());
        assertEquals(1, playlist.getSongs().size());
        assertEquals(200L, playlist.getDuration());
        assertEquals(1, playlist.getCounter());
        verify(playlistRepository).save(playlist);
        verify(userRepository).save(user);
    }

    @Test
    void addSongShouldThrowWhenPlaylistMissing() {
        User user = new User();
        user.setId(1L);

        Song song = new Song();
        song.setId(5L);

        when(songService.getSongById(5L)).thenReturn(song);
        when(userService.getCurrentUser()).thenReturn(user);
        when(playlistRepository.findById(10L)).thenReturn(Optional.empty());

        PlaylistNotFoundException exception = assertThrows(
                PlaylistNotFoundException.class,
                () -> playlistService.addSong(5L, 10L)
        );

        assertEquals("Playlist not found", exception.getMessage());
        verify(playlistRepository, never()).save(any());
        verify(userRepository, never()).save(user);
    }

    @Test
    void addSongShouldThrowWhenUserHasPlaylistOwnedByAnotherUser() {
        User currentUser = new User();
        currentUser.setId(1L);

        User anotherUser = new User();
        anotherUser.setId(2L);

        Playlist anotherUserPlaylist = new Playlist();
        anotherUserPlaylist.setId(99L);
        anotherUserPlaylist.setUser(anotherUser);
        currentUser.setPlaylists(new ArrayList<>());

        Song song = new Song();
        song.setId(5L);

        Playlist targetPlaylist = new Playlist();
        targetPlaylist.setId(10L);
        targetPlaylist.setUser(anotherUser);

        when(songService.getSongById(5L)).thenReturn(song);
        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(targetPlaylist));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> playlistService.addSong(5L, 10L)
        );

        assertEquals("You're can't add this song to this playlist", exception.getMessage());
        verify(playlistRepository, never()).save(any());
        verify(userRepository, never()).save(currentUser);
    }

    @Test
    void getPlaylistsByArtistNameOrAlbumNameShouldReturnMappedPlaylists() {
        SongDTO songDTO = new SongDTO(1L, "artist", "album", 10L, LocalDateTime.now(), null, null, 120, null, null, null, 1L, null, null);
        PlaylistDTO playlistDTO = new PlaylistDTO(2L, Set.of(songDTO), 10L, 120L, true, 1, LocalDateTime.now(), "cover.jpg", "image/jpeg", "Favorites");
        Playlist playlist = new Playlist();

        when(userRepository.existsById(1L)).thenReturn(true);
        when(playlistRepository.existsPlaylistByArtistNameOrAlbumNameAndUserId("artist", "album", 1L)).thenReturn(true);
        when(playlistRepository.findByArtistNameOrAlbumNameAndUserId("artist", "album", 1L)).thenReturn(List.of(playlist));
        when(playlistMapper.toPlaylistDTO(List.of(playlist))).thenReturn(List.of(playlistDTO));

        List<PlaylistDTO> result = playlistService.getPlaylistsByArtistNameOrAlbumName("artist", "album", 1L);

        assertEquals(1, result.size());
        assertEquals("artist", result.get(0).songs().iterator().next().artistName());
    }

    @Test
    void getPlaylistsByArtistNameOrAlbumNameShouldThrowWhenUserMissing() {
        when(userRepository.existsById(1L)).thenReturn(false);

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> playlistService.getPlaylistsByArtistNameOrAlbumName("artist", "album", 1L)
        );

        assertEquals("User not found with 1", exception.getMessage());
    }

    @Test
    void getPlaylistsByArtistNameOrAlbumNameShouldThrowWhenPlaylistMissing() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(playlistRepository.existsPlaylistByArtistNameOrAlbumNameAndUserId("artist", "album", 1L)).thenReturn(false);

        PlaylistNotFoundException exception = assertThrows(
                PlaylistNotFoundException.class,
                () -> playlistService.getPlaylistsByArtistNameOrAlbumName("artist", "album", 1L)
        );

        assertEquals("Playlist not found with artist album", exception.getMessage());
    }

    @Test
    void changeVisibilityShouldUpdatePlaylistWhenOwnerChangesVisibility() {
        User user = new User();
        user.setId(1L);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setUser(user);
        playlist.setPublic(false);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(userService.getCurrentUser()).thenReturn(user);

        playlistService.changeVisibility(10L, true);

        assertEquals(true, playlist.isPublic());
        verify(playlistRepository).save(playlist);
    }

    @Test
    void changeVisibilityShouldThrowWhenCurrentUserIsNotOwner() {
        User owner = new User();
        owner.setId(1L);

        User currentUser = new User();
        currentUser.setId(2L);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setUser(owner);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(userService.getCurrentUser()).thenReturn(currentUser);

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> playlistService.changeVisibility(10L, true)
        );

        assertEquals("You're can't change this playlist", exception.getMessage());
        verify(playlistRepository, never()).save(playlist);
    }

    @Test
    void changeVisibilityShouldDoNothingWhenVisibilityAlreadyMatches() {
        User user = new User();
        user.setId(1L);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setUser(user);
        playlist.setPublic(true);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(userService.getCurrentUser()).thenReturn(user);

        playlistService.changeVisibility(10L, true);

        verify(playlistRepository, never()).save(playlist);
    }

    @Test
    void deletePlaylistShouldDeletePlaylistWhenCurrentUserIsOwner() {
        User user = new User();
        user.setId(1L);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setUser(user);

        when(userService.getCurrentUser()).thenReturn(user);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        playlistService.deletePlaylist(10L);

        verify(playlistRepository).delete(playlist);
    }

    @Test
    void deletePlaylistShouldThrowWhenPlaylistMissing() {
        User user = new User();
        user.setId(1L);

        when(userService.getCurrentUser()).thenReturn(user);
        when(playlistRepository.findById(10L)).thenReturn(Optional.empty());

        PlaylistNotFoundException exception = assertThrows(
                PlaylistNotFoundException.class,
                () -> playlistService.deletePlaylist(10L)
        );

        assertEquals("Playlist not found with 10", exception.getMessage());
        verify(playlistRepository, never()).delete(any());
    }

    @Test
    void deletePlaylistShouldThrowWhenCurrentUserIsNotOwner() {
        User currentUser = new User();
        currentUser.setId(1L);

        User owner = new User();
        owner.setId(2L);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setUser(owner);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> playlistService.deletePlaylist(10L)
        );

        assertEquals("You can't delete this playlist", exception.getMessage());
        verify(playlistRepository, never()).delete(playlist);
    }

    @Test
    void getPlaylistShouldReturnMappedPlaylist() {
        User current = new User();
        current.setId(1L);

        User user = new User();
        user.setId(2L);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setUser(current);
        playlist.setPublic(false);

        user.setPlaylists(List.of(playlist));

        PlaylistDTO playlistDTO = new PlaylistDTO(10L, null, 0, 0, false, 0, null, null, null, null);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userService.getCurrentUser()).thenReturn(current);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(playlistMapper.toPlaylistDTO(playlist)).thenReturn(playlistDTO);

        PlaylistDTO result = playlistService.getPlaylist(10L, 2L);

        assertEquals(playlistDTO, result);
        assertEquals(10L, result.id());
    }

    @Test
    void getPlaylistShouldThrowNotFoundPlaylist() {
        User current = new User();
        current.setId(1L);

        User user = new User();
        user.setId(2L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userService.getCurrentUser()).thenReturn(current);
        when(playlistRepository.findById(10L)).thenReturn(Optional.empty());

        PlaylistNotFoundException exception = assertThrows(
                PlaylistNotFoundException.class,
                () -> playlistService.getPlaylist(10L, 2L));

        assertEquals("Playlist not found with 10", exception.getMessage());
    }

    @Test
    void getPlaylistShouldThrowExceptionIfUserNotAdmin() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        User user = new User();
        user.setId(2L);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setUser(user);
        playlist.setPublic(false);

        user.setPlaylists(List.of(playlist));

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userService.getCurrentUser()).thenReturn(current);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> playlistService.getPlaylist(10L, 2L)
        );
        assertEquals("You're can't get this playlist", exception.getMessage());
    }

    @Test
    void getPlaylistShouldThrowNotFoundWhenUserDoesNotHavePlaylist() {
        User current = new User();
        current.setId(1L);

        User user = new User();
        user.setId(2L);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setUser(user);
        playlist.setPublic(false);

        Playlist playlist1 = new Playlist();
        playlist1.setId(11L);

        user.setPlaylists(List.of(playlist1));

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userService.getCurrentUser()).thenReturn(current);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        PlaylistNotFoundException exception = assertThrows(
                PlaylistNotFoundException.class,
                () -> playlistService.getPlaylist(10L, 2L));

        assertEquals("Playlist not found with 10", exception.getMessage());
    }
}
