package zhedron.playlist.dto;

import zhedron.playlist.enums.Provider;
import zhedron.playlist.enums.Role;

import java.time.LocalDateTime;
import java.util.List;



public record UserDTO(long id, String email, LocalDateTime createdAt,
                      Role role, boolean blocked, Provider provider,
                      String name, String about, String profilePicture,
                      String contentType, String phone,
                      boolean isHiddenPhone, LocalDateTime updatedAt, List<PlaylistDTO> playlists, List<AlbumDTO> albums, List<SubscriptionDTO> subscriptions) {
    public UserDTO getByPhone(String phone) {
        return new UserDTO(id, email, createdAt, role, blocked, provider,
                name, about, profilePicture,
                contentType, phone, isHiddenPhone, updatedAt, playlists, albums, subscriptions);
    }
}
