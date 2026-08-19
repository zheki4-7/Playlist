package zhedron.playlist.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.SongDTO;
import zhedron.playlist.dto.request.SongRequest;
import zhedron.playlist.dto.request.SongUpdateRequest;
import zhedron.playlist.dto.response.MessageResponse;
import zhedron.playlist.dto.response.PaginatedResponse;
import zhedron.playlist.entity.Song;
import zhedron.playlist.mapper.SongMapper;
import zhedron.playlist.repository.SongRepository;
import zhedron.playlist.services.SongService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/song")
@Tag(name = "Song API", description = "This API contains create, update, delete, play song and top 10 songs.")
public class SongController {
    private final SongService songService;
    private final SongMapper songMapper;
    private final SongRepository songRepository;

    private final String PATH = "song/";
    private final String IMAGEPATH = "song/image/";

    @Autowired
    public SongController(SongService songService, SongMapper songMapper, SongRepository songRepository) {
        this.songService = songService;
        this.songMapper = songMapper;
        this.songRepository = songRepository;
    }

    @PostMapping(value = "/create", consumes =  MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "Playlist")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(encoding = @Encoding(name = "requestSong", contentType = MediaType.APPLICATION_JSON_VALUE)))
    @Operation(summary = "Upload and create a new song", description = "Create a new song metadata record and upload its audio and cover image files")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully uploaded song and created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SongDTO.class))),
            @ApiResponse(responseCode = "400", description = "Not successfully create song or upload song",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"), examples = {
                    @ExampleObject(
                            name = "Empty name",
                            value = "{\"error\": \"Name must not be empty\"}",
                            summary = "Triggered when the song name is empty"
                    ),
                    @ExampleObject(
                            name = "Null name",
                            value = "{\"error\": \"Name must not be null\"}",
                            summary = "Triggered when the song name is missing from the request"
                    ),
                    @ExampleObject(
                            name = "Empty album",
                            value = "{\"error\": \"Album must not be empty\"}",
                            summary = "Triggered when the album field is empty"
                    ),
                    @ExampleObject(
                            name = "Invalid audio format",
                            value = "{\"message\": \"Upload audio file.\"}",
                            summary = "Triggered when the uploaded audio file is not MP4 or MPEG"
                    ),
                    @ExampleObject(
                            name = "Invalid image format",
                            value = "{\"message\": \"Upload image file.\"}",
                            summary = "Triggered when the uploaded cover image is not JPEG or PNG"
                    )
            }))
    })
    public ResponseEntity<?> createSong(@Valid @RequestPart SongRequest requestSong, BindingResult result, @RequestPart List<MultipartFile> files, @RequestPart MultipartFile image) throws IOException {
        if (result.hasErrors()) {
            Map<String, String> errors = new HashMap<>();

            for (FieldError error : result.getFieldErrors()) {
                errors.put("error", error.getDefaultMessage());
            }
            return ResponseEntity.badRequest().body(errors);
        }
        for (MultipartFile file : files) {
            if (!file.getContentType().equals("audio/mp4") && !file.getContentType().equals("audio/mpeg") || file.isEmpty()) {
                return ResponseEntity.badRequest().body(new MessageResponse("Upload audio file."));
            }
        }
        if (!image.getContentType().equals("image/jpeg") && !image.getContentType().equals("image/png")) {
            return ResponseEntity.badRequest().body(new MessageResponse("Upload image file."));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(songService.save(requestSong, files, image));
    }

    @DeleteMapping("/delete/{id}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Delete a song by ID", description = "Permanently remove a song from the application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully deleted song",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Song {id} deleted\"}"))),
            @ApiResponse(responseCode = "404", description = "Not found a song or user",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Song not found with {id}\"}"))),
            @ApiResponse(responseCode = "403", description = "User didn't log in")
    })
    public ResponseEntity<MessageResponse> deleteSong(@PathVariable long id) {
        songService.deleteSongById(id);

        return ResponseEntity.ok(new MessageResponse("Song " + id + " deleted"));
    }

    @GetMapping("/top")
    @Operation(summary = "Retrieve top 10 songs", description = "Retrieve detailed metadata for a specific song by view listeners")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved top songs",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = SongDTO.class)))),
    })
    public List<SongDTO> topSongs() {
        return songService.getTopSongs();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find a song by ID", description = "Retrieve detailed metadata for a specific song")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully found song",
            content = @Content(schema = @Schema(implementation = SongDTO.class))),
            @ApiResponse(responseCode = "404", description = "Not found a song",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Song not found with {id}\"}")))
    })
    public SongDTO findSongById(@PathVariable long id) {
        Song song = songService.getSongById(id);

        return songMapper.songToSongDTO(song);
    }

    @GetMapping("/file/{id}")
    @Operation(summary = "Stream song audio file", description = "Fetch and stream the raw audio binary for playback")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully found song and play",
            content = @Content(mediaType = "audio/mpeg", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Not found a song",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Song not found with {id}\"}"))),
            @ApiResponse(responseCode = "400", description = "A file not loaded",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Cannot read file.\"}")))
    })
    public ResponseEntity<?> getSongById (@PathVariable long id) {
        Song song = songService.getSongById(id);

        try {
            Path path = Paths.get(PATH).resolve(song.getFileName()).normalize();

            Resource resource = new UrlResource(path.toUri());

            song.setListeners(song.getListeners() + 1);

            songRepository.save(song);

            return ResponseEntity.ok().contentType(MediaType.parseMediaType(song.getContentType())).body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/perweek")
    @Operation(summary = "Get paginated weekly songs", description = "Retrieve a paginated list of songs trending during the current week")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved paginated songs",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PaginatedResponse.class)))
    })
    public ResponseEntity<PaginatedResponse> findAllPerWeek(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(songService.findAllPerWeek(page, size));
    }

    @GetMapping("/search")
    @Operation(summary = "Search songs by artist or album")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Find songs matching either the specified artist name, album name, or both",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = SongDTO.class)))),
            @ApiResponse(responseCode = "404", description = "Not found artist or album",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"), examples = {
                    @ExampleObject(
                            name = "Artist not found",
                            value = "{\"message\": \"Song not found with {artistName}\"}",
                            summary = "Triggered when no songs match the provided artist name"
                    ),
                    @ExampleObject(
                            name = "Album not found",
                            value = "{\"message\": \"Song not found with {albumName}\"}",
                            summary = "Triggered when no songs match the provided album name"
                    ),
                    @ExampleObject(
                            name = "Artist and Album not found",
                            value = "{\"message\": \"Song not found with {artistName} and {albumName}\"}",
                            summary = "Triggered when no songs match the combination of both names"
                    )
            }))
    })
    public ResponseEntity<List<SongDTO>> findAllByArtistNameOrAlbumName(@RequestParam(required = false) String artistName, @RequestParam(required = false) String albumName) {
        List<SongDTO> songs = songService.findByArtistNameOrAlbumName(artistName, albumName);

        return ResponseEntity.ok().body(songs);
    }

    @GetMapping("/my-uploads")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Get current user's uploads", description = "Retrieve a list of all songs uploaded by the currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user's uploads",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            array = @ArraySchema(schema = @Schema(implementation = SongDTO.class))))
    })
    public ResponseEntity<List<SongDTO>> findAllByMyUploads() {
        return ResponseEntity.ok().body(songService.findAllByMyUploads());
    }

    @GetMapping("/image/{id}")
    @Operation(summary = "Get song cover image", description = "Fetch and display the raw binary image file for a song's cover art")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cover image found",
            content = {
                    @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary"))
            }),
            @ApiResponse(responseCode = "404", description = "Not found song",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Song not found with {id}\"}")))
    })
    public ResponseEntity<?> getImageBySongId(@PathVariable long id) {
        Song song = songService.getSongById(id);

        if (song != null) {
            Path path = Paths.get(IMAGEPATH).resolve(song.getImagePath()).normalize();

            try {
                Resource resource = new UrlResource(path.toUri());

                return ResponseEntity.ok().contentType(MediaType.parseMediaType(song.getContentType())).body(resource);
            } catch (IOException e) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error loading image"));
            }
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/status/{id}")
    public ResponseEntity<SongDTO> changeStatus(@PathVariable long id, @RequestBody SongUpdateRequest songUpdateRequest) {
        return ResponseEntity.ok(songService.changeStatus(id, songUpdateRequest));
    }
}
