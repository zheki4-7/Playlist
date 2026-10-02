package zhedron.playlist.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.AlbumDTO;
import zhedron.playlist.dto.request.AlbumRequest;
import zhedron.playlist.dto.response.MessageResponse;
import zhedron.playlist.entity.Album;
import zhedron.playlist.mapper.AlbumMapper;
import zhedron.playlist.services.AlbumService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/album")
@Tag(name = "Album Controller", description = "Album management endpoints (creation, retrieval, deletion, and adding tracks)")
public class AlbumController {
    private final AlbumService albumService;

    private final AlbumMapper albumMapper;

    public AlbumController(AlbumService albumService, AlbumMapper albumMapper) {
        this.albumService = albumService;
        this.albumMapper = albumMapper;
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new album", description = "Creates a new album with an uploaded cover image.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Album successfully created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AlbumDTO.class))),
            @ApiResponse(responseCode = "400", description = "Validation error in request payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    public ResponseEntity<?> createAlbum(@RequestPart @Valid AlbumRequest albumRequest, BindingResult bindingResult, @RequestPart MultipartFile image) throws IOException {
        if (bindingResult.hasErrors()) {
            for (FieldError error : bindingResult.getFieldErrors()) {
                Map<String, Object> response = new HashMap<>();

                response.put("message",  error.getDefaultMessage());

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(albumService.createAlbum(albumRequest, image));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get album metadata by ID", description = "Retrieves album details by album ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Album found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AlbumDTO.class))),
            @ApiResponse(responseCode = "404", description = "Album not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    public ResponseEntity<AlbumDTO> getAlbumById(@PathVariable long id) {
        Album album = albumService.findById(id);

        AlbumDTO albumDTO = albumMapper.albumToAlbumDTO(album);

        return ResponseEntity.ok(albumDTO);
    }

    @GetMapping("/image/{id}")
    @Operation(summary = "Get album cover image by ID", description = "Returns the binary image file of the album cover.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Album not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message:\" \"Album not found with 1}\""))),
            @ApiResponse(responseCode = "500", description = "Error reading image file from storage")
    })
    public ResponseEntity<Resource> getAlbumImageById(@PathVariable long id, WebRequest webRequest) {
        Album album = albumService.findById(id);

        try {
            Path path = Paths.get("art_album/").resolve(album.getCoverArtUrl()).normalize();

            Resource resource = new UrlResource(path.toUri());

            long lastModified = resource.lastModified();

            if (webRequest.checkNotModified(lastModified)) {
                return null;
            }

            return ResponseEntity.status(HttpStatus.OK)
                    .cacheControl(CacheControl.noCache().sMaxAge(7, TimeUnit.DAYS))
                    .lastModified(lastModified)
                    .contentType(MediaType.parseMediaType(album.getContentType()))
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "Delete album by ID", description = "Deletes an album. Only the creator of the album is authorized to delete it.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Album successfully deleted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (user is not the album creator)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Album not found", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message:\" \"Album not found with 1\"}")))
    })
    public ResponseEntity<MessageResponse> deleteAlbumById(@PathVariable long id) {
        albumService.deleteById(id);

        return ResponseEntity.ok(new MessageResponse("Album has been deleted"));
    }

    @PostMapping("/add/{albumId}/{songId}")
    @Operation(summary = "Add a song to an album", description = "Links a song to an album. Allowed only if the user is the creator of either the album or the song.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Song successfully added to album",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AlbumDTO.class))),
            @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(mediaType =  MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", examples = "{\"message:\" \"You can't add this song to this album\"}"))),
            @ApiResponse(responseCode = "404", description = "Album or song not found", content = @Content(examples = {
                    @ExampleObject(
                            name = "Song not found",
                            value = "{\"message: \" \"Song not found with 1\"}",
                            summary = "Triggered if song was not found"
                    ),
                    @ExampleObject(
                            name = "Album not found",
                            value = "{\"message:\" \"Album not found with 1\"}",
                            summary = "Triggered if album was not found"
                    )
            }))
    })
    public ResponseEntity<AlbumDTO> addSongToAlbum(@PathVariable long albumId, @PathVariable long songId) {
        return ResponseEntity.ok(albumService.addSongToAlbum(albumId, songId));
    }
}
