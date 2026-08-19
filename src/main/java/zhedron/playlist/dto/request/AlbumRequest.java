package zhedron.playlist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlbumRequest {
    @NotNull(message = "Enter a title")
    @NotBlank(message = "Title must not be empty")
    private String title;
}
