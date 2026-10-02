package zhedron.playlist.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import zhedron.playlist.config.SecurityConfig;
import zhedron.playlist.config.filter.JwtFilter;
import zhedron.playlist.dto.PlaylistDTO;
import zhedron.playlist.dto.SongDTO;
import zhedron.playlist.dto.request.PlaylistRequest;
import zhedron.playlist.entity.Playlist;
import zhedron.playlist.entity.Song;
import zhedron.playlist.exceptions.AccessDeniedException;
import zhedron.playlist.exceptions.PlaylistNotFoundException;
import zhedron.playlist.exceptions.SongNotFoundException;
import zhedron.playlist.exceptions.UserNotFoundException;
import zhedron.playlist.repository.UserRepository;
import zhedron.playlist.services.CustomOauth2UserService;
import zhedron.playlist.services.JwtService;
import zhedron.playlist.services.PlaylistService;
import zhedron.playlist.services.impl.UserDetailsImpl;
import zhedron.playlist.success.handlers.GoogleSuccessHandler;

import java.time.LocalDateTime;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(PlaylistController.class)
@Import(SecurityConfig.class)
class PlaylistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private JwtFilter jwtFilter;

    @MockitoBean
    private PlaylistService playlistService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomOauth2UserService oauth2UserService;

    @MockitoBean
    private GoogleSuccessHandler googleSuccessHandler;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserDetailsImpl userDetails;

    @Test
    @WithMockUser(username = "test", password = "test")
    void addSongInPlaylistShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(post("/playlist/add/1/10").param("public", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Playlist added"));

        verify(playlistService).addSong(1L, 10L);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void addSongInPlaylistShouldReturnNotFoundWhenSongDoesNotExist() throws Exception {
        doThrow(new SongNotFoundException("Song not found with 1"))
                .when(playlistService).addSong(1L, 10L);

        mockMvc.perform(post("/playlist/add/1/10").param("public", "false"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Song not found with 1"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void findByArtistNameOrAlbumNameShouldReturnPlaylists() throws Exception {
        SongDTO song = new SongDTO(1L, "artist", "album", 10L, LocalDateTime.now(), null, null, 120, null, null, null, 7L, null, null);
        PlaylistDTO playlist = new PlaylistDTO(1L, Set.of(song), 10L, 120L, true, 1, LocalDateTime.now(), "cover.jpg", "image/jpeg", "Favorites");

        when(playlistService.getPlaylistsByArtistNameOrAlbumName("artist", "album", 7L))
                .thenReturn(List.of(playlist));

        mockMvc.perform(get("/playlist/search")
                        .param("artistName", "artist")
                        .param("albumName", "album")
                        .param("userId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].songs[0].artistName").value("artist"))
                .andExpect(jsonPath("$[0].songs[0].albumName").value("album"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void findByArtistNameOrAlbumNameShouldReturnUserNotFound() throws Exception {
        when(playlistService.getPlaylistsByArtistNameOrAlbumName("artist", "album", 7L))
                .thenThrow(new UserNotFoundException("User not found with 7"));

        mockMvc.perform(get("/playlist/search")
                        .param("artistName", "artist")
                        .param("albumName", "album")
                        .param("userId", "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found with 7"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void findByArtistNameOrAlbumNameShouldReturnPlaylistNotFound() throws Exception {
        when(playlistService.getPlaylistsByArtistNameOrAlbumName("artist", "album", 7L))
                .thenThrow(new PlaylistNotFoundException("Playlist not found with artist album"));

        mockMvc.perform(get("/playlist/search")
                        .param("artistName", "artist")
                        .param("albumName", "album")
                        .param("userId", "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Playlist not found with artist album"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void changeAvailableShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(post("/playlist/available/10").param("public", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changed to public visibility"));

        verify(playlistService).changeVisibility(10L, true);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void createPlaylistShouldReturnCreatedMessage() throws Exception {
        MockMultipartFile requestPart = new MockMultipartFile(
                "playlistRequest",
                "",
                "application/json",
                "{\"title\":\"Favorites\",\"public\":true}".getBytes()
        );
        MockMultipartFile imagePart = new MockMultipartFile(
                "file",
                "cover.jpg",
                "image/jpeg",
                "image".getBytes()
        );

        mockMvc.perform(multipart("/playlist/create")
                        .file(requestPart)
                        .file(imagePart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Playlist created"));

        verify(playlistService).savePlaylist(any(PlaylistRequest.class), any());
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void createPlaylistShouldRejectInvalidImageContentType() throws Exception {
        MockMultipartFile requestPart = new MockMultipartFile(
                "playlistRequest",
                "",
                "application/json",
                "{\"title\":\"Favorites\",\"public\":true}".getBytes()
        );
        MockMultipartFile filePart = new MockMultipartFile(
                "file",
                "track.mp3",
                "audio/mpeg",
                "audio".getBytes()
        );

        mockMvc.perform(multipart("/playlist/create")
                        .file(requestPart)
                        .file(filePart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Upload image"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void deletePlaylistShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(delete("/playlist/delete/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Playlist deleted successfully"));

        verify(playlistService).deletePlaylist(10L);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void deletePlaylistShouldReturnNotFoundWhenPlaylistMissing() throws Exception {
        doThrow(new PlaylistNotFoundException("Playlist not found with 10"))
                .when(playlistService).deletePlaylist(10L);

        mockMvc.perform(delete("/playlist/delete/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Playlist not found with 10"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void getPlaylistImageShouldReturnCacheControlAndLastModified() throws Exception {
        String imageUrl = Paths.get("playlist_image", "test IMG_20260626_134219434_MFNR.jpg")
                .toAbsolutePath()
                .toUri()
                .toString();
        PlaylistDTO playlistDTO = new PlaylistDTO(
                10L, Set.of(), 0L, 0L, true, 0, LocalDateTime.now(), imageUrl, "image/jpeg", "Favorites");

        when(playlistService.findPlaylistById(10L)).thenReturn(playlistDTO);

        mockMvc.perform(get("/playlist/image/10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("s-maxage=86400")))
                .andExpect(header().exists("Last-Modified"))
                .andExpect(header().string("Content-Type", "image/jpeg"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void getPlaylistShouldReturnPlaylist() throws Exception {
        Playlist playlist = new Playlist();

        playlist.setId(10L);

        Song song = new Song();

        song.setId(10L);

        SongDTO songDTO = new SongDTO(song.getId(), null, null, 0, null, null, null, 0, null, null, null, 0, null, null);

        Set<SongDTO> songs = new HashSet<>();

        songs.add(songDTO);

        PlaylistDTO playlistDTO = new PlaylistDTO(playlist.getId(), songs, 0, 0, false, 0, null, null, null, null);

        when(playlistService.getPlaylist(playlist.getId(), song.getId())).thenReturn(playlistDTO);

        mockMvc.perform(get("/playlist/10/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.songs[0].id").value(10L));

        verify(playlistService).getPlaylist(10L, 10L);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void getPlaylistShouldReturnNotFoundWhenPlaylistMissing() throws Exception {
        doThrow(new PlaylistNotFoundException("Playlist not found with 10")).when(playlistService).getPlaylist(10L, 10L);

        mockMvc.perform(get("/playlist/10/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Playlist not found with 10"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void getPlaylistShouldReturnIfPlaylistNotPublicAndUserNotAdmin() throws Exception {
        doThrow(new AccessDeniedException("You're can't get this playlist")).when(playlistService).getPlaylist(10L, 10L);

        mockMvc.perform(get("/playlist/10/10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You're can't get this playlist"));
    }

    @Test
    @WithMockUser(username = "test", password = "test", authorities = "ADMIN")
    void getPlaylistShouldReturnIfPlaylistNotPublicAndUserAdmin() throws Exception {
        Playlist playlist = new Playlist();

        playlist.setId(10L);

        Song song = new Song();

        song.setId(10L);

        SongDTO songDTO = new SongDTO(song.getId(), null, null, 0, null, null, null, 0, null, null, null, 0, null, null);

        Set<SongDTO> songs = new HashSet<>();

        songs.add(songDTO);

        PlaylistDTO playlistDTO = new PlaylistDTO(playlist.getId(), songs, 0, 0, false, 0, null, null, null, null);

        when(playlistService.getPlaylist(playlist.getId(), song.getId())).thenReturn(playlistDTO);

        mockMvc.perform(get("/playlist/10/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.songs[0].id").value(10L));

        verify(playlistService).getPlaylist(10L, 10L);
    }
}
