package zhedron.playlist.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import zhedron.playlist.dto.AlbumDTO;
import zhedron.playlist.dto.SongDTO;
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
import zhedron.playlist.repository.UserRepository;
import zhedron.playlist.services.impl.AlbumServiceImpl;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AlbumServiceTest {
    @InjectMocks
    private AlbumServiceImpl albumService;

    @Mock
    private UserService userService;

    @Mock
    private AlbumMapper albumMapper;

    @Mock
    private UserRepository userRepository;
    @Mock
    private SongRepository songRepository;
    @Mock
    private AlbumRepository albumRepository;

    @Test
    public void createAlbum() throws IOException {
        User currentUser = new User();

        currentUser.setId(1L);
        currentUser.setEmail("test@test.com");
        currentUser.setPassword("test");

        AlbumRequest albumRequest = new AlbumRequest();
        albumRequest.setTitle("title");

        Album album = new Album();

        album.setId(1L);
        album.setTitle(albumRequest.getTitle());
        album.setCreator(currentUser);

        MockMultipartFile mockMultipartFile = new MockMultipartFile("image", "image.jpg", MediaType.IMAGE_JPEG_VALUE, "test".getBytes());

        AlbumDTO albumDTO = new AlbumDTO(1L, 0L, null, "title", null, null);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(albumRepository.save(any(Album.class))).thenReturn(album);
        when(albumMapper.albumToAlbumDTO(any(Album.class))).thenReturn(albumDTO);

        AlbumDTO saved = albumService.createAlbum(albumRequest, mockMultipartFile);

        assertEquals(1L, saved.id());
        assertEquals("title", saved.title());
    }

    @Test
    public void deleteAlbumById() {
        User currentUser = new User();
        currentUser.setId(1L);

        Album album = new Album();
        album.setId(1L);
        album.setCreator(currentUser);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));

        albumService.deleteById(1L);

        verify(albumRepository).deleteById(1L);
    }

    @Test
    public void deleteAlbumByIdIsNotFound() {
        User currentUser = new User();
        currentUser.setId(1L);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(albumRepository.findById(1L)).thenReturn(Optional.empty());

        AlbumNotFoundException albumNotFoundException = assertThrows(
                AlbumNotFoundException.class,
                () -> albumService.deleteById(1L)
        );

        assertEquals("Album not found with 1", albumNotFoundException.getMessage());
        verify(albumRepository, never()).deleteById(1L);
    }

    @Test
    public void findAlbumById() {
        Album album = new Album();
        album.setId(1L);

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));

        Album foundAlbum = albumService.findById(1L);

        assertEquals(1L, foundAlbum.getId());

        verify(albumRepository).findById(1L);
    }

    @Test
    public void findAlbumByIdIsNotFound() {
        when(albumRepository.findById(1L)).thenReturn(Optional.empty());

        AlbumNotFoundException albumNotFoundException = assertThrows(
                AlbumNotFoundException.class,
                () -> albumService.findById(1L)
        );

        assertEquals("Album not found with 1", albumNotFoundException.getMessage());
        verify(albumRepository).findById(1L);
    }

    @Test
    public void addSongToAlbumIsOk() {
        User currentUser = new User();
        currentUser.setId(1L);

        Song song = new Song();
        song.setId(1L);
        song.setType(Type.SINGLE);
        song.setDuration(5);
        song.setCreator(currentUser);

        Album album = new Album();
        album.setId(1L);
        album.setCreator(currentUser);
        album.setDuration(1L);

        SongDTO songDTO = new SongDTO(1L, null, null, 0, null, null, null, 0, Type.SINGLE, null, null, 1L, null, null);

        AlbumDTO albumDTO = new AlbumDTO(1L, 1L, List.of(songDTO), "title", null, null);

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(albumRepository.save(any(Album.class))).thenReturn(album);
        when(albumMapper.albumToAlbumDTO(any(Album.class))).thenReturn(albumDTO);

        AlbumDTO saved = albumService.addSongToAlbum(1L, 1L);

        assertEquals(1L, saved.id());
        assertEquals("title", saved.title());
        assertEquals(1L, saved.songs().get(0).id());

        verify(albumRepository).save(any(Album.class));
    }

    @Test
    public void addSongToAlbumIsNotFoundAlbum() {
        doThrow(new AlbumNotFoundException("Album not found with 1")).when(albumRepository).findById(1L);

        AlbumNotFoundException albumNotFoundException = assertThrows(
                AlbumNotFoundException.class,
                () -> albumService.addSongToAlbum(1L, 1L)
        );

        assertEquals("Album not found with 1", albumNotFoundException.getMessage());
        verify(albumRepository).findById(1L);
    }

    @Test
    public void addSongToAlbumIsNotFoundSong() {
        Album album = new Album();
        album.setId(1L);

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        doThrow(new SongNotFoundException("Song not found with 1")).when(songRepository).findById(1L);

        SongNotFoundException songNotFoundException = assertThrows(
                SongNotFoundException.class,
                () -> albumService.addSongToAlbum(1L, 1L)
        );

        assertEquals("Song not found with 1", songNotFoundException.getMessage());
    }

    @Test
    public void addSongToAlbumIsAccessDenied() throws IOException {
        User currentUser = new User();
        currentUser.setId(1L);

        User fakeUser = new User();
        fakeUser.setId(2L);

        Song song = new Song();
        song.setId(1L);
        song.setType(Type.SINGLE);
        song.setDuration(5);
        song.setCreator(fakeUser);

        Album album = new Album();
        album.setId(1L);
        album.setCreator(fakeUser);
        album.getSongs().add(song);
        album.setDuration(1L);

        when(albumRepository.findById(1L)).thenReturn(Optional.of(album));
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(userService.getCurrentUser()).thenReturn(currentUser);

        AccessDeniedException accessDeniedException = assertThrows(
                AccessDeniedException.class,
                () -> albumService.addSongToAlbum(1L, 1L)
        );

        assertEquals("You can't add this song to this album", accessDeniedException.getMessage());
    }
}
