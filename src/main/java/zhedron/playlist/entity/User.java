package zhedron.playlist.entity;

import jakarta.persistence.*;
import lombok.Data;
import zhedron.playlist.enums.Provider;
import zhedron.playlist.enums.Role;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String name;

    private String about;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    private String phone;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    @OneToMany(mappedBy = "user")
    private List<Playlist> playlists;

    private boolean blocked;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    private String profilePicture;

    private String contentType;

    @OneToMany(fetch =  FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.REMOVE, CascadeType.MERGE}, orphanRemoval = true, mappedBy = "creator")
    private List<Album> albums;

    private boolean isHiddenPhone;

    @OneToMany(mappedBy = "subscriber")
    private List<Subscription> subscriptions;
}
