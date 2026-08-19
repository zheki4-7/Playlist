package zhedron.playlist.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import zhedron.playlist.dto.PlaylistDTO;
import zhedron.playlist.dto.UserDTO;
import zhedron.playlist.entity.Playlist;
import zhedron.playlist.entity.User;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {SongMapper.class, SubscriptionMapper.class})
public interface UserMapper {
    @Mapping(target = "isHiddenPhone", source = "hiddenPhone")
    UserDTO userToUserDTO(User user);

    @Mapping(target = "isPublic", source = "public")
    PlaylistDTO playlistToPlaylistDTO(Playlist playlist);

    User userDTOtoUser(UserDTO userDTO);
}
