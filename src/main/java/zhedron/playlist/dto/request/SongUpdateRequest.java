package zhedron.playlist.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import zhedron.playlist.enums.Status;

import java.time.LocalDateTime;

@Data
public class SongUpdateRequest {
    private Status status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;
}
