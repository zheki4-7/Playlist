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
import zhedron.playlist.config.SecurityConfig;
import zhedron.playlist.config.filter.JwtFilter;
import zhedron.playlist.dto.SongDTO;
import zhedron.playlist.dto.request.SongRequest;
import zhedron.playlist.dto.response.PaginatedResponse;
import zhedron.playlist.entity.Song;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Type;
import zhedron.playlist.exceptions.SongNotFoundException;
import zhedron.playlist.exceptions.UserNotEnoughPermissionsException;
import zhedron.playlist.mapper.SongMapper;
import zhedron.playlist.repository.SongRepository;
import zhedron.playlist.repository.UserRepository;
import zhedron.playlist.services.*;
import zhedron.playlist.services.impl.UserDetailsImpl;
import zhedron.playlist.success.handlers.GoogleSuccessHandler;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.containsString;

@WebMvcTest(SongController.class)
@Import(SecurityConfig.class)
class SongControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JwtFilter jwtFilter;

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

    @MockitoBean
    private SongService songService;

    @MockitoBean
    private SongMapper songMapper;

    @MockitoBean
    private SongRepository songRepository;

    @Test
    void getSongByIdShouldReturnSongDto() throws Exception {
        User creator = new User();
        creator.setId(42L);

        Song song = new Song();
        song.setId(1L);
        song.setListeners(2L);
        song.setCreator(creator);

        SongDTO songDTO = new SongDTO(1L, "artist", "album", 3L, LocalDateTime.now(), "audio/mpeg", "track.mp3", 120, Type.SINGLE, null, null, 42L, null, null);

        when(songService.getSongById(1L)).thenReturn(song);
        when(songMapper.songToSongDTO(song)).thenReturn(songDTO);

        mockMvc.perform(get("/song/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.artistName").value("artist"));
    }

    @Test
    void getSongByIdShouldReturnNotFound() throws Exception {
        when(songService.getSongById(1L)).thenThrow(new SongNotFoundException("Song not found with 1"));

        mockMvc.perform(get("/song/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Song not found with 1"));
    }

    @Test
    void getSongFileShouldReturnCacheControlAndAudioContent() throws Exception {
        Song song = new Song();
        song.setId(1L);
        song.setFileName("1_test_test_december.mp3");
        song.setContentType("audio/mpeg");

        when(songService.getSongById(1L)).thenReturn(song);

        mockMvc.perform(get("/song/file/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("s-maxage=604800")))
                .andExpect(header().string("Content-Type", "audio/mpeg"));

        verify(songRepository).save(song);
    }

    @Test
    void getSongImageShouldReturnCacheControlAndLastModified() throws Exception {
        Song song = new Song();
        song.setId(1L);
        song.setImagePath("1_test_Classroom POV D.png");
        song.setContentTypeImage("image/png");

        when(songService.getSongById(1L)).thenReturn(song);

        mockMvc.perform(get("/song/image/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().exists("Last-Modified"))
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void createSongShouldReturnCreatedSongs() throws Exception {
        SongRequest songRequest = new SongRequest();
        songRequest.setArtistName("artist");
        songRequest.setAlbumName("album");

        MockMultipartFile requestPart = new MockMultipartFile("requestSong", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(songRequest));
        MockMultipartFile audioPart = new MockMultipartFile("files", "track.mp3", "audio/mpeg", "audio".getBytes());
        MockMultipartFile imagePart = new MockMultipartFile("image", "cover.jpg", MediaType.IMAGE_JPEG_VALUE, "image".getBytes());

        SongDTO response = new SongDTO(1L, "artist", "album", 0L, LocalDateTime.now(), "audio/mpeg", "track.mp3", 120, Type.SINGLE, "cover.jpg", "image/jpeg", 7L, null, null);

        when(songService.save(any(SongRequest.class), anyList(), any()))
                .thenReturn(List.of(response));

        mockMvc.perform(multipart("/song/create")
                        .file(audioPart)
                        .file(requestPart)
                        .file(imagePart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].artistName").value("artist"))
                .andExpect(jsonPath("$[0].albumName").value("album"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void createSongShouldRejectInvalidAudioContentType() throws Exception {
        SongRequest songRequest = new SongRequest();
        songRequest.setArtistName("artist");
        songRequest.setAlbumName("album");
        MockMultipartFile requestPart = new MockMultipartFile("requestSong", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(songRequest));
        MockMultipartFile audioPart = new MockMultipartFile("files", "track.jpg", MediaType.IMAGE_JPEG_VALUE, "wrong".getBytes());
        MockMultipartFile imagePart = new MockMultipartFile("image", "cover.jpg", MediaType.IMAGE_JPEG_VALUE, "image".getBytes());

        mockMvc.perform(multipart("/song/create")
                        .file(audioPart)
                        .file(requestPart)
                        .file(imagePart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Upload audio file."));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void deleteSongShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(delete("/song/delete/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Song 1 deleted"));

        verify(songService).deleteSongById(1L);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void deleteSongShouldReturnPermissionsError() throws Exception {
        doThrow(new UserNotEnoughPermissionsException("You do not have enough permissions to delete this song"))
                .when(songService).deleteSongById(1L);

        mockMvc.perform(delete("/song/delete/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You do not have enough permissions to delete this song"));
    }

    @Test
    void topSongsShouldReturnResponseFromService() throws Exception {
        when(songService.getTopSongs()).thenReturn(List.of(
                new SongDTO(3L, "artist", "album", 99L, LocalDateTime.now(), null, null, 0, Type.SINGLE, null, null, 1L, null, null)
        ));

        mockMvc.perform(get("/song/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].listeners").value(99));
    }

    @Test
    void findAllPerWeekShouldReturnPaginatedResponse() throws Exception {
        PaginatedResponse response = new PaginatedResponse(
                List.of(new SongDTO(1L, "artist", "album", 10L, LocalDateTime.now(), null, null, 180, Type.SINGLE, null, null, 1L, null, null)),
                0,
                10,
                1L,
                1,
                true,
                true,
                false,
                false
        );

        when(songService.findAllPerWeek(0, 10)).thenReturn(response);

        mockMvc.perform(get("/song/perweek"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songs[0].artistName").value("artist"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
