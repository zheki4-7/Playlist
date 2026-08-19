package zhedron.playlist.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import zhedron.playlist.dto.AlbumDTO;
import zhedron.playlist.entity.Album;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AlbumMapper {
    AlbumDTO albumToAlbumDTO(Album album);

    Album albumDTOToAlbum(AlbumDTO albumDTO);
}
