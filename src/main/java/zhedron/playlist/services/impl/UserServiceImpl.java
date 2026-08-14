package zhedron.playlist.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.PlaylistDTO;
import zhedron.playlist.dto.SubscriptionDTO;
import zhedron.playlist.dto.UserDTO;
import zhedron.playlist.dto.request.UserRequest;
import zhedron.playlist.dto.request.UserUpdateRequest;
import zhedron.playlist.entity.Playlist;
import zhedron.playlist.entity.Song;
import zhedron.playlist.entity.Subscription;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Provider;
import zhedron.playlist.enums.Role;
import zhedron.playlist.exceptions.*;
import zhedron.playlist.mapper.SubscriptionMapper;
import zhedron.playlist.mapper.UserMapper;
import zhedron.playlist.repository.PlaylistRepository;
import zhedron.playlist.repository.SubscriptionRepository;
import zhedron.playlist.repository.UserRepository;
import zhedron.playlist.services.AESEncryptionService;
import zhedron.playlist.services.EmailService;
import zhedron.playlist.services.UserService;


import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final UserMapper userMapper;

    private final SubscriptionMapper subscriptionMapper;

    private final PlaylistRepository playlistRepository;

    private final AESEncryptionService aesEncryptionService;

    private final EmailService emailService;

    private final String PATH = "profile_image/";
    private final SubscriptionRepository subscriptionRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper, SubscriptionMapper subscriptionMapper, PlaylistRepository playlistRepository, AESEncryptionService aesEncryptionService, EmailService emailService, SubscriptionRepository subscriptionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.playlistRepository = playlistRepository;
        this.aesEncryptionService = aesEncryptionService;
        this.emailService = emailService;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public User save(UserRequest requestUser) {
        if (requestUser.getPhone() != null) {

            String encryptedPhone = aesEncryptionService.encrypt(requestUser.getPhone());

            if (userRepository.existsByEmail(requestUser.getEmail())) {
                throw new UserExistException("Email already exists, use other email");
            } else if (requestUser.getPhone() != null && userRepository.existsByPhone(encryptedPhone)) {
                throw new PhoneExistException("Phone already exists, use phone number");
            }
        }

        User user = new User();

        if (requestUser.getAbout() == null || requestUser.getAbout().isEmpty()) {
            user.setAbout("There is no description");
        }
        user.setProfilePicture("1646346915_1-abrakadabra-fun-p-standartnaya-avatarka-standoff-3.jpg");
        user.setContentType("image/jpeg");
        user.setPassword(passwordEncoder.encode(requestUser.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setBlocked(false);
        user.setProvider(Provider.LOCAL);
        user.setPhone(requestUser.getPhone() != null ? aesEncryptionService.encrypt(requestUser.getPhone()) : null);
        user.setEmail(requestUser.getEmail());
        user.setName(requestUser.getName());
        user.setAbout(requestUser.getAbout());
        user.setHiddenPhone(true);

        String text = "Congratulations! Your account successfully created! Now you listen to music, create song and add your favorite songs to your playlist";

        emailService.sendTo(user.getEmail(), "Your account created", text);

        log.info("Saved user {}", user);

        return userRepository.save(user);
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found with " + email));
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public UserDTO getById(long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found with " + id));

        return userMapper.userToUserDTO(user);
    }

    @Override
    @Cacheable(value = "users", key = "#userId")
    public List<PlaylistDTO> getPlaylists(long userId) {
        UserDTO user = getById(userId);

        List<Playlist> playlists = userRepository.findByUserId(userId);

        List<PlaylistDTO> playlistResponses = null;

        for (Playlist playlist : playlists) {
            if (user.id() == playlist.getUser().getId()) {
                playlistResponses = playlists.stream().map(userMapper::playlistToPlaylistDTO).toList();
            } else {
                playlistResponses = playlists.stream().filter(Playlist::isPublic).map(userMapper::playlistToPlaylistDTO).toList();
            }
        }

        return playlistResponses;
    }

    @Override
    @Caching(cacheable = {
            @Cacheable(value = "playlists", key = "#playlistId"),
            @Cacheable(value = "songs", key = "#songId")
    })
    public void deleteSongFromPlaylist(long playlistId, long songId) {
        User user = getCurrentUser();

        for (Playlist userPlaylist : user.getPlaylists()) {
            if (userPlaylist.getId() != playlistId) {
                throw new PlaylistNotFoundException("Playlist not found with " + playlistId);
            }
        }

        Playlist playlist = playlistRepository.findByIdAndSongId(playlistId, songId);

        Song song = user.getPlaylists().stream()
                .flatMap(p -> p.getSongs().stream())
                .filter(s -> s.getId() == songId).findFirst().orElseThrow(() -> new SongNotFoundException("Song not found with " + songId));

        playlist.setDuration(playlist.getDuration() - song.getDuration());

        playlist.getSongs().remove(song);

        playlistRepository.save(playlist);

        log.info("Deleted songId {} from playlist {}", song.getId(), playlist.getId());
    }

    @Override
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String email = auth.getName();

        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.isBlocked()) {
            throw new UserBlockedException("You are blocked");
        }

        return user;
    }

    @Override
    @Cacheable(value = "users", key = "#userId")
    public void blockUser(long userId) {
        UserDTO userDTO = getById(userId);

        User user = userMapper.userDTOtoUser(userDTO);

        user.setBlocked(true);

        userRepository.save(user);

        log.info("Blocked user {}", user);
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public Resource getProfilePicture(long id) throws MalformedURLException {
        UserDTO user = getById(id);

        return new UrlResource(user.profilePicture());
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    public void updateUser(UserUpdateRequest userUpdate, long userId) throws Exception {
        UserDTO userDTO = getById(userId);

        User user = userMapper.userDTOtoUser(userDTO);

        User currentUser = getCurrentUser();

        String subject = "Your data for your account changed";
        String text = null;

        if (currentUser.getId() != userId && !currentUser.getRole().equals(Role.ADMIN)) {
            throw new Exception("You can't change.");
        }

        if (userUpdate.getEmail() != null) {
            user.setEmail(userUpdate.getEmail());

            text = "Your email changed to " + userUpdate.getEmail();
        }
        if (userUpdate.getPassword() != null) {
            if (passwordEncoder.matches(userUpdate.getPassword(), user.getPassword())) {
                throw new Exception("You use the same password.");
            }

            user.setPassword(passwordEncoder.encode(userUpdate.getPassword()));
        }
        if (userUpdate.getAbout() != null) {
            user.setAbout(userUpdate.getAbout());
        }

        if (userUpdate.getName() != null) {
            user.setName(userUpdate.getName());
        }
        if (userUpdate.getPhone() != null) {
            user.setPhone(aesEncryptionService.encrypt(userUpdate.getPhone()));
        }
        if (userUpdate.getIsHiddenPhone() != null) {
            if (user.getPhone() == null && userUpdate.getIsHiddenPhone()) {
                throw new Exception("You can't hide phone, because your phone empty");
            }
            user.setHiddenPhone(userUpdate.getIsHiddenPhone());
        }

        user.setUpdatedAt(LocalDateTime.now());

        log.info("Updated user.");

        userRepository.save(user);

        if (text != null) {
            String textWarn = "If you haven't done, please contact to technical support";

            emailService.sendTo(user.getEmail(), subject, text + "\n" + textWarn);
        }
    }

    @Override
    @Cacheable(value = "users", key = "#userId")
    public void changeRole(Role role, long userId) {
        User currentUser = getCurrentUser();

        UserDTO userDTO = getById(userId);

        User user = userMapper.userDTOtoUser(userDTO);

        user.setRole(role);

        log.info("User {} changed role of {} to {}", currentUser.getName(), user.getName(), role.name());

        userRepository.save(user);
    }

    @Override
    public void uploadAvatar(MultipartFile file) {
        User user = getCurrentUser();

        String profilePicture = user.getProfilePicture();

        String fileName = profilePicture.substring(profilePicture.indexOf("_") + 1);

        if (!fileName.equals(file.getOriginalFilename())) {
            Path path = Paths.get(PATH).resolve(user.getProfilePicture()).normalize();

            try {
                boolean deleted = Files.deleteIfExists(path);
                if (deleted) log.info("File deleted successfully");
            } catch (IOException e) {
                throw new RuntimeException("Cannot delete file.", e);
            }
        }

        String namePicture = user.getId() + "_" + file.getOriginalFilename();

        Path path = Paths.get(PATH).resolve(namePicture).normalize();

        user.setProfilePicture(namePicture);
        user.setContentType(file.getContentType());

        try {
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Error while uploading avatar", e);
        }

        userRepository.save(user);
    }

    @Override
    @Cacheable(value = "subscriptions", key = "#subscriberId")
    public List<SubscriptionDTO> getSubscriptions(long subscriberId) {
        List<Subscription> subscriptions = subscriptionRepository.getSubscriptionsBySubscriberId(subscriberId);

        return subscriptionMapper.toSubscriptionsDTO(subscriptions);
    }
}
