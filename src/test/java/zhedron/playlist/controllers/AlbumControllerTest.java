package zhedron.playlist.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.config.SecurityConfig;
import zhedron.playlist.config.filter.JwtFilter;
import zhedron.playlist.dto.AlbumDTO;
import zhedron.playlist.dto.SongDTO;
import zhedron.playlist.dto.request.AlbumRequest;
import zhedron.playlist.entity.Album;
import zhedron.playlist.entity.Song;
import zhedron.playlist.entity.User;
import zhedron.playlist.exceptions.AlbumNotFoundException;
import zhedron.playlist.exceptions.SongNotFoundException;
import zhedron.playlist.mapper.AlbumMapper;
import zhedron.playlist.repository.AlbumRepository;
import zhedron.playlist.repository.UserRepository;
import zhedron.playlist.services.AlbumService;
import zhedron.playlist.services.CustomOauth2UserService;
import zhedron.playlist.services.JwtService;
import zhedron.playlist.services.UserService;
import zhedron.playlist.services.impl.UserDetailsImpl;
import zhedron.playlist.success.handlers.GoogleSuccessHandler;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(AlbumController.class)
@Import(SecurityConfig.class)
public class AlbumControllerTest {
    @Autowired
    private MockMvc mockMvc;

    private JwtFilter jwtFilter;

    @MockitoBean
    private AlbumService albumService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomOauth2UserService oauth2UserService;

    @MockitoBean
    private GoogleSuccessHandler googleSuccessHandler;

    @MockitoBean
    private AlbumRepository albumRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserDetailsImpl userDetails;

    @MockitoBean
    private AlbumMapper albumMapper;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(username = "test", password = "test")
    public void createAlbumIsCreated() throws Exception {
        User currentUser = new User();

        currentUser.setId(1L);
        currentUser.setEmail("test@test.com");
        currentUser.setPassword("test");

        AlbumRequest albumRequest = new AlbumRequest();

        albumRequest.setTitle("test");

        AlbumDTO albumDTO = new AlbumDTO(1L, 0, null, "test", null, null);

        MockMultipartFile multipartFile = new MockMultipartFile("image", "image.jpg", "image/jpeg", "test".getBytes());
        MockMultipartFile albumRequestFile = new MockMultipartFile("albumRequest", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(albumRequest));

        when(albumService.createAlbum(any(AlbumRequest.class), any(MultipartFile.class))).thenReturn(albumDTO);

        mockMvc.perform(multipart("/album/create")
                        .file(multipartFile)
                        .file(albumRequestFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("test"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    public void createAlbumIsFailed() throws Exception {
        AlbumRequest albumRequest = new AlbumRequest();
        albumRequest.setTitle("");

        AlbumDTO albumDTO = new AlbumDTO(1L, 0, null, "", null, null);

        MockMultipartFile multipartFile = new MockMultipartFile("image", "image.jpg", "image/jpeg", "test".getBytes());
        MockMultipartFile albumRequestFile = new MockMultipartFile("albumRequest", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(albumRequest));

        when(albumService.createAlbum(any(AlbumRequest.class), any(MultipartFile.class))).thenReturn(albumDTO);

        mockMvc.perform(multipart("/album/create")
                .file(multipartFile)
                .file(albumRequestFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Title must not be empty"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    public void findAlbumByIdIsOk() throws Exception {
        Album album = new Album();
        album.setId(1L);

        AlbumDTO albumDTO = new AlbumDTO(1L, 0, null, null, null, null);

        when(albumService.findById(anyLong())).thenReturn(album);
        when(albumMapper.albumToAlbumDTO(album)).thenReturn(albumDTO);

        mockMvc.perform(get("/album/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    public void findAlbumByIdIsNotFound() throws Exception {
        when(albumService.findById(anyLong())).thenThrow(new AlbumNotFoundException("Album not found with 1"));

        mockMvc.perform(get("/album/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Album not found with 1"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    public void deleteAlbumIsOk() throws Exception {
        mockMvc.perform(delete("/album/delete/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Album has been deleted"));

        verify(albumService).deleteById(anyLong());
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    public void deleteAlbumIsNotFound() throws Exception {
        doThrow(new AlbumNotFoundException("Album not found with 1")).when(albumService).deleteById(anyLong());

        mockMvc.perform(delete("/album/delete/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Album not found with 1"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    public void addSongToAlbumIsOk() throws Exception {
        Album album = new Album();
        album.setId(1L);

        Song song = new Song();
        song.setId(1L);

        SongDTO songDTO = new SongDTO(1L, null, null, 0, null, null, null, 0, null, null, null, 0L, null, null);

        AlbumDTO albumDTO = new AlbumDTO(1L, 0, List.of(songDTO), null, null, null);

        when(albumService.addSongToAlbum(anyLong(), anyLong())).thenReturn(albumDTO);

        mockMvc.perform(post("/album/add/1/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.songs[0].id").value(1));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    public void addSongToAlbumIsNotFoundSong() throws Exception {
        doThrow(new SongNotFoundException("Song not found with 1")).when(albumService).addSongToAlbum(anyLong(), anyLong());

        mockMvc.perform(post("/album/add/1/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Song not found with 1"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    public void addSongToAlbumIsNotFoundAlbum() throws Exception {
        doThrow(new AlbumNotFoundException("Album not found with 1")).when(albumService).addSongToAlbum(anyLong(), anyLong());

        mockMvc.perform(post("/album/add/1/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Album not found with 1"));
    }
}