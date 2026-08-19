package zhedron.playlist.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import zhedron.playlist.dto.PlaylistDTO;
import zhedron.playlist.dto.SongDTO;
import zhedron.playlist.dto.UserDTO;
import zhedron.playlist.dto.request.UserRequest;
import zhedron.playlist.dto.request.UserUpdateRequest;
import zhedron.playlist.entity.Playlist;
import zhedron.playlist.entity.Song;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Provider;
import zhedron.playlist.enums.Role;
import zhedron.playlist.enums.Type;
import zhedron.playlist.exceptions.PhoneExistException;
import zhedron.playlist.exceptions.PlaylistNotFoundException;
import zhedron.playlist.exceptions.UserNotFoundException;
import zhedron.playlist.mapper.UserMapper;
import zhedron.playlist.repository.PlaylistRepository;
import zhedron.playlist.repository.UserRepository;
import zhedron.playlist.services.impl.UserServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private AESEncryptionService aesEncryptionService;

    @Mock
    private EmailService emailService;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private UserServiceImpl userService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getByIdShouldReturnUser() {
        User user = new User();
        user.setId(1L);

        UserDTO userDTO = new UserDTO(user.getId(), null, null, null, false, null, null, null, null, null, null, false, null, List.of(), List.of(), List.of());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.userToUserDTO(user)).thenReturn(userDTO);

        UserDTO result = userService.getById(1L);

        assertEquals(1L, result.id());
    }

    @Test
    void getByIdShouldThrowWhenUserMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getById(1L));
    }

    @Test
    void saveShouldEncodePasswordAndEncryptPhone() {
        UserRequest request = new UserRequest();
        request.setName("test");
        request.setEmail("test@test.com");
        request.setPassword("secret");
        request.setPhone("+394111215988");
        request.setAbout("about");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByPhone("+394111215988")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-password");
        when(aesEncryptionService.encrypt(request.getPhone())).thenReturn("+394111215988");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User savedUser = userService.save(request);

        assertEquals("encoded-password", savedUser.getPassword());
        assertEquals("+394111215988", savedUser.getPhone());
        assertEquals(Provider.LOCAL, savedUser.getProvider());
        assertTrue(savedUser.isHiddenPhone());
    }

    @Test
    void saveShouldThrowWhenEncryptedPhoneAlreadyExists() {
        UserRequest request = new UserRequest();
        request.setName("test");
        request.setEmail("test@test.com");
        request.setPassword("secret");
        request.setPhone("+394111215988");

        when(aesEncryptionService.encrypt(request.getPhone())).thenReturn("+394111215988");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByPhone("+394111215988")).thenReturn(true);

        PhoneExistException exception = assertThrows(
                PhoneExistException.class,
                () -> userService.save(request)
        );

        assertEquals("Phone already exists, use phone number", exception.getMessage());
    }

    @Test
    void getPlaylistsShouldMapUserPlaylists() {
        User user = new User();
        user.setId(1L);

        UserDTO userDTO = new UserDTO(user.getId(), null, null, null, false, null, null, null, null, null, null, false, null, List.of(), List.of(), List.of());

        Song song = new Song();
        song.setId(7L);
        song.setArtistName("artist");
        song.setAlbumName("album");
        song.setCreatedAt(LocalDateTime.now());
        song.setType(Type.SINGLE);

        Playlist playlist = new Playlist();
        playlist.setId(3L);
        playlist.setUser(user);
        playlist.setSongs(Set.of(song));
        playlist.setPublic(true);
        playlist.setCreatedAt(LocalDateTime.now());

        PlaylistDTO playlistDTO = new PlaylistDTO(
                3L,
                Set.of(new SongDTO(7L, "artist", "album", 0L, song.getCreatedAt(), null, null, 0, Type.SINGLE, null, null, 1L, null, null)),
                0L,
                0L,
                true,
                1,
                playlist.getCreatedAt(),
                "cover.jpg",
                "image/jpeg",
                "Favorites"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.userToUserDTO(user)).thenReturn(userDTO);
        when(userRepository.findByUserId(1L)).thenReturn(List.of(playlist));
        when(userMapper.playlistToPlaylistDTO(playlist)).thenReturn(playlistDTO);

        List<PlaylistDTO> result = userService.getPlaylists(1L);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).id());
    }

    @Test
    void updateUserShouldAllowAdminToChangeAnotherUser() throws Exception {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("admin@test.com");
        currentUser.setRole(Role.ADMIN);

        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setEmail("old@test.com");
        targetUser.setPassword("old-password");

        UserDTO userDTO = new UserDTO(targetUser.getId(), targetUser.getEmail(), null, null, false, null, null, null, null, null, null, false, null, List.of(), List.of(), List.of());

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setEmail("new@test.com");
        updateRequest.setPassword("new-password");
        updateRequest.setPhone("+394111215989");

        mockCurrentUser(currentUser);

        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userMapper.userToUserDTO(targetUser)).thenReturn(userDTO);
        when(userMapper.userDTOtoUser(userDTO)).thenReturn(targetUser);
        when(passwordEncoder.matches("new-password", "old-password")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");
        when(aesEncryptionService.encrypt("+394111215989")).thenReturn("encrypted-phone");

        userService.updateUser(updateRequest, 2L);

        assertEquals("new@test.com", targetUser.getEmail());
        assertEquals("encoded-password", targetUser.getPassword());
        assertEquals("encrypted-phone", targetUser.getPhone());
        assertNotNull(targetUser.getUpdatedAt());
    }

    @Test
    void changeRoleShouldUpdateRole() {
        User currentUser = new User();
        currentUser.setId(2L);
        currentUser.setName("test");
        currentUser.setRole(Role.ADMIN);
        currentUser.setEmail("test@test.com");

        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);

        UserDTO userDTO = new UserDTO(user.getId(), user.getEmail(), null, null, false, null, null, null, null, null, null, false, null, List.of(), List.of(), List.of());

        mockCurrentUser(currentUser);

        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.userToUserDTO(user)).thenReturn(userDTO);
        when(userMapper.userDTOtoUser(userDTO)).thenReturn(user);

        userService.changeRole(Role.ADMIN, 1L);

        assertEquals(Role.ADMIN, user.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void deleteSongFromPlaylistShouldRemoveSongAndUpdateDuration() {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("test@test.com");

        Song song = new Song();
        song.setId(5L);
        song.setDuration(120);

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setDuration(300L);
        playlist.setSongs(new java.util.HashSet<>(Set.of(song)));

        currentUser.setPlaylists(List.of(playlist));

        mockCurrentUser(currentUser);

        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
        when(playlistRepository.findByIdAndSongId(10L, 5L)).thenReturn(playlist);

        userService.deleteSongFromPlaylist(10L, 5L);

        assertEquals(180L, playlist.getDuration());
        assertTrue(playlist.getSongs().isEmpty());
        verify(playlistRepository).save(playlist);
    }

    @Test
    void deleteSongFromPlaylistShouldThrowWhenPlaylistMissing() {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("test@test.com");

        Playlist playlist = new Playlist();
        playlist.setId(10L);
        currentUser.setPlaylists(List.of(playlist));

        mockCurrentUser(currentUser);

        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));

        PlaylistNotFoundException exception = assertThrows(
                PlaylistNotFoundException.class,
                () -> userService.deleteSongFromPlaylist(99L, 5L)
        );

        assertEquals("Playlist not found with 99", exception.getMessage());
    }

    private void mockCurrentUser(User currentUser) {
        when(authentication.getName()).thenReturn(currentUser.getEmail());
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}
