package zhedron.playlist.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import zhedron.playlist.dto.PlaylistDTO;
import zhedron.playlist.dto.SongDTO;
import zhedron.playlist.dto.UserDTO;
import zhedron.playlist.dto.request.UserRequest;
import zhedron.playlist.dto.request.UserUpdateRequest;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Provider;
import zhedron.playlist.enums.Role;
import zhedron.playlist.enums.Type;
import zhedron.playlist.exceptions.PlaylistNotFoundException;
import zhedron.playlist.exceptions.SubscribedException;
import zhedron.playlist.exceptions.UserNotFoundException;
import zhedron.playlist.mapper.UserMapper;
import zhedron.playlist.repository.UserRepository;
import zhedron.playlist.services.*;
import zhedron.playlist.services.impl.UserDetailsImpl;
import zhedron.playlist.success.handlers.GoogleSuccessHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JwtFilter jwtFilter;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private AESEncryptionService aesEncryptionService;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private CustomOauth2UserService oauth2UserService;

    @MockitoBean
    private GoogleSuccessHandler googleSuccessHandler;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserDetailsImpl userDetails;

    private User user;
    private UserDTO userDTO;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setName("test");
        user.setAbout("about");
        user.setProvider(Provider.LOCAL);
        user.setCreatedAt(LocalDateTime.now());

        userDTO = new UserDTO(
                1L,
                "test@test.com",
                user.getCreatedAt(),
                Role.USER,
                null,
                false,
                Provider.LOCAL,
                "test",
                "about",
                "avatar.jpg",
                "image/jpeg",
                "encrypted-phone",
                false,
                null,
                null
        );
    }

    @Test
    void createUserShouldReturnCreatedUser() throws Exception {
        UserRequest request = new UserRequest();
        request.setName("test");
        request.setEmail("test@test.com");
        request.setPassword("secret");
        request.setPhone("+394111215988");
        request.setAbout("about");

        when(userService.save(any(UserRequest.class))).thenReturn(user);
        when(userMapper.userToUserDTO(user)).thenReturn(userDTO);

        mockMvc.perform(post("/user/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.email").value("test@test.com"));
    }

    @Test
    void getUserByIdShouldReturnDecryptedPhoneWhenVisible() throws Exception {
        when(userService.getById(1L)).thenReturn(userDTO);
        when(aesEncryptionService.decrypt("encrypted-phone")).thenReturn("+394111215988");

        mockMvc.perform(get("/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+394111215988"));
    }

    @Test
    void getUserByIdShouldReturnNotFound() throws Exception {
        when(userService.getById(2L)).thenThrow(new UserNotFoundException("User not found with 2"));

        mockMvc.perform(get("/user/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found with 2"));
    }

    @Test
    @WithMockUser(username = "test", password = "test", authorities = "ADMIN")
    void blockUserShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(put("/user/block/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User blocked successfully"));

        verify(userService).blockUser(1L);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void blockUserShouldReturnIfUserNotAdmin() throws Exception {
        mockMvc.perform(put("/user/block/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void updateUserShouldReturnSuccessMessage() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("updated@test.com");
        request.setPhone("+394111215989");

        doNothing().when(userService).updateUser(any(UserUpdateRequest.class), any(Long.class));

        mockMvc.perform(patch("/user/update/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User updated successfully"));
    }

    @Test
    @WithMockUser(username = "test", password = "test", authorities = "ADMIN")
    void changeRoleShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(put("/user/change_role/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Changed role to USER successfully"));

        verify(userService).changeRole(Role.USER, 1L);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void changeRoleShouldReturnIfUserNotAdmin() throws Exception {
        mockMvc.perform(put("/user/change_role/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void uploadAvatarShouldRejectInvalidContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "audio.mp3", "audio/mpeg", "audio".getBytes());

        mockMvc.perform(multipart("/user/upload-avatar").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid image jpg or png format"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void uploadAvatarShouldRejectEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "", MediaType.IMAGE_PNG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/user/upload-avatar").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File must not be null or empty"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void deleteSongFromPlaylistShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(delete("/user/playlist/delete/1/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Song from playlist deleted successfully"));

        verify(userService).deleteSongFromPlaylist(1L, 2L);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void deleteSongFromPlaylistShouldReturnPlaylistNotFound() throws Exception {
        doThrow(new PlaylistNotFoundException("Playlist not found with 1"))
                .when(userService).deleteSongFromPlaylist(1L, 2L);

        mockMvc.perform(delete("/user/playlist/delete/1/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Playlist not found with 1"));
    }

    @Test
    void getPlaylistsShouldReturnPlaylistList() throws Exception {
        SongDTO song = new SongDTO(3L, "artist", "album", 5L, LocalDateTime.now(), null, null, 180, Type.SINGLE, null, null, 1L, null, null);
        PlaylistDTO playlist = new PlaylistDTO(11L, Set.of(song), 5L, 180L, true, 1, LocalDateTime.now(), "cover.jpg", "image/jpeg", "Favorites");

        when(userService.getPlaylists(1L)).thenReturn(List.of(playlist));

        mockMvc.perform(get("/user/playlists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].songs[0].albumName").value("album"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void subscribeToUserShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(post("/user/subscribe/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User subscribed successfully"));

        verify(subscriptionService).subscribeToUser(2L);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void subscribeToUserShouldReturnForbiddenWhenAlreadySubscribed() throws Exception {
        doThrow(new SubscribedException("You're already subscribed!"))
                .when(subscriptionService).subscribeToUser(2L);

        mockMvc.perform(post("/user/subscribe/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You're already subscribed!"));
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void unsubscribeFromUserShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(post("/user/unsubscribe/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User unsubscribed successfully"));

        verify(subscriptionService).unsubscribeFromUser(2L);
    }

    @Test
    @WithMockUser(username = "test", password = "test")
    void unsubscribeFromUserShouldReturnForbiddenWhenNotSubscribed() throws Exception {
        doThrow(new SubscribedException("You're not subscribed!"))
                .when(subscriptionService).unsubscribeFromUser(2L);

        mockMvc.perform(post("/user/unsubscribe/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You're not subscribed!"));
    }
}
