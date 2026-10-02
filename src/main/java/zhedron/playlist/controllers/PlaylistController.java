package zhedron.playlist.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.PlaylistDTO;
import zhedron.playlist.dto.request.PlaylistRequest;
import zhedron.playlist.dto.response.MessageResponse;
import zhedron.playlist.services.PlaylistService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/playlist")
@Tag(name = "Playlist API", description = "This API contains add song in playlist user")
public class PlaylistController {
    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @PostMapping("/add/{songId}/{playlistId}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Add a song to the playlist", description = "Add a specific song to a user's playlist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully added song in playlist user",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Playlist added\"}"))),
            @ApiResponse(responseCode = "404", description = "Not found a song",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Song not found with {id}\"}"))),
            @ApiResponse(responseCode = "403", description = "Can't song to playlist",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"You're can't add this song to this playlist\"}")))
    })
    public ResponseEntity<MessageResponse> addPlayList(@PathVariable long songId, @PathVariable long playlistId) {
        playlistService.addSong(songId, playlistId);

        return ResponseEntity.ok(new MessageResponse("Playlist added"));
    }

    @GetMapping("/search")
    @Operation(summary = "Search playlists by artist or album", description = "Retrieve user playlists that contain songs by a specific artist or album name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully found songs artist or album from playlist user",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = PlaylistDTO.class))
                    )),
            @ApiResponse(responseCode = "404", description = "Not found artist or album",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"), examples = {
                            @ExampleObject(
                                    name = "Not found a album or artist",
                                    value = "{\"message\": \"Artist name or album name not found\"}",
                                    summary = "Triggered when no matching artist or album name exists in the database"
                            ),
                            @ExampleObject(
                                    name = "Not found a user",
                                    value = "{\"message\": \"User not found with {id}\"}",
                                    summary = "Triggered when the provided userId does not match any existing user"
                            )
                    }))
    })
    public ResponseEntity<List<PlaylistDTO>> findByArtistNameOrAlbumName(@RequestParam(required = false) String artistName,
                                                                         @RequestParam(required = false) String albumName,
                                                                         @RequestParam long userId) {
        return ResponseEntity.ok(playlistService.getPlaylistsByArtistNameOrAlbumName(artistName, albumName, userId));
    }

    @PostMapping("/available/{playlistId}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Change playlist visibility", description = "Toggle playlist between public and private status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated visibility",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Changed to public/private visibility\"}"))),
            @ApiResponse(responseCode = "404", description = "Not found playlist",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Playlist not found with {playlistId}\"}"))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"You're can't change this playlist\"}")))
    })
    public ResponseEntity<MessageResponse> changeVisibility(@PathVariable long playlistId, @RequestParam("public") boolean isPublic) {
        playlistService.changeVisibility(playlistId, isPublic);

        if (isPublic) {
            return ResponseEntity.ok(new MessageResponse("Changed to public visibility"));
        } else {
            return ResponseEntity.ok(new MessageResponse("Changed to private visibility"));
        }
    }

    @PostMapping(value = "/create")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Create a new playlist", description = "Create a new playlist with an attached cover image")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Playlist created successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Playlist created\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid file type",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"), examples = {
                    @ExampleObject(
                            name = "Unsupported file format",
                            value = "{\"message\": \"Upload image\"}",
                            summary = "Triggered when the uploaded file is not a JPEG or PNG image"
                    ),
                    @ExampleObject(
                            name = "File empty dropped",
                            value = "{\"message\": \"File couldn't be empty\"}",
                            summary = "Triggered when the file part is present but contains no data"
                    )
            }))
    })
    public ResponseEntity<MessageResponse> createPlaylist(@RequestPart PlaylistRequest playlistRequest, @RequestPart MultipartFile file) throws IOException {
        if (!file.isEmpty() && (!file.getContentType().equals("image/jpeg") && !file.getContentType().equals("image/png"))) {
            return ResponseEntity.badRequest().body(new MessageResponse("Upload image"));
        } else if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("File couldn't be empty"));
        }

        playlistService.savePlaylist(playlistRequest, file);

        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse("Playlist created"));
    }

    @DeleteMapping("/delete/{id}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Delete a playlist by ID", description = "Remove an existing playlist from the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully deleted playlist",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Playlist deleted successfully\"}"))),
            @ApiResponse(responseCode = "404", description = "Not found playlist",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Playlist not found with {id}\"}"))),
            @ApiResponse(responseCode = "403", description = "You can't delete playlist",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"You can't delete this playlist\"}")))
    })
    public ResponseEntity<MessageResponse> deletePlaylist(@PathVariable long id) {
        playlistService.deletePlaylist(id);

        return ResponseEntity.ok(new MessageResponse("Playlist deleted successfully"));
    }

    @GetMapping("/image/{id}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Get playlist cover image", description = "Returns the cover image of the playlist by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image retrieved successfully",
            content = @Content(mediaType = "image/*")),
            @ApiResponse(responseCode = "403", description = "Forbidden playlist",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"You're can't find this playlist\"}")))
    })
    public ResponseEntity<Resource> getImage(@PathVariable long id, WebRequest webRequest) throws IOException {
        PlaylistDTO playlistDTO = playlistService.findPlaylistById(id);

        Resource resource = new UrlResource(playlistDTO.imageURL());

        if (!resource.exists()) {
            return ResponseEntity.internalServerError().build();
        }

        long lastModified = resource.lastModified();

        if (webRequest.checkNotModified(lastModified)) {
            return null;
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().sMaxAge(1, TimeUnit.DAYS))
                .lastModified(lastModified)
                .contentType(MediaType.parseMediaType(playlistDTO.contentType()))
                .body(resource);
    }

    @GetMapping("/{playlistId}/{userId}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Get playlist by ID", description = "Returns playlist with songs for the specified user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Playlist retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PlaylistDTO.class))),
            @ApiResponse(responseCode = "404", description = "Playlist or user not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                    @ExampleObject(
                            name = "User not found",
                            value = "{\"message\": \"User not found with {id}\"}",
                            summary = "Triggered when user not found"
                    ),
                    @ExampleObject(
                            name = "Playlist not found",
                            value = "{\"message\": \"Playlist not found with {id}\"}",
                            summary = "Triggered when playlist not found"
                    )
            })),
            @ApiResponse(responseCode = "403", description = "Access denied — playlist is private",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"You're can't get this playlist\"}")))
    })
    public ResponseEntity<PlaylistDTO> getPlaylist(@PathVariable long playlistId, @PathVariable long userId) {
        return ResponseEntity.ok(playlistService.getPlaylist(playlistId, userId));
    }
}
