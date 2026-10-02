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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import zhedron.playlist.dto.PlaylistDTO;
import zhedron.playlist.dto.SubscriptionDTO;
import zhedron.playlist.dto.UserDTO;
import zhedron.playlist.dto.request.UserRequest;
import zhedron.playlist.dto.request.UserUpdateRequest;
import zhedron.playlist.dto.response.MessageResponse;
import zhedron.playlist.entity.User;
import zhedron.playlist.enums.Provider;
import zhedron.playlist.enums.Role;
import zhedron.playlist.mapper.UserMapper;
import zhedron.playlist.services.AESEncryptionService;
import zhedron.playlist.services.SubscriptionService;
import zhedron.playlist.services.UserService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User API",
        description = "This User API can create, update, delete, block user and find user or songs from user playlist")
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;
    private final AESEncryptionService aesEncryptionService;
    private final SubscriptionService subscriptionService;

    private final String PICTURE_PATH = "profile_image/";

    @Autowired
    public UserController(UserService userService, UserMapper userMapper, AESEncryptionService aesEncryptionService, SubscriptionService subscriptionService) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.aesEncryptionService = aesEncryptionService;
        this.subscriptionService = subscriptionService;
    }

    @PostMapping(value = "/registration")
    @Operation(summary = "Register a new user", description = "Create a new user account in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
            description = "User created successfully",
            content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400",
            description = "Validation failed for registration data",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"), examples = {
                    @ExampleObject(
                            name = "Empty name",
                            value = "{\"error\": \"Name must not be empty\"}",
                            summary = "Triggered when the provided name is empty or missing"
                    ),
                    @ExampleObject(
                            name = "Missing email",
                            value = "{\"error\": \"Write your email\"}",
                            summary = "Triggered when the email field is not provided"
                    ),
                    @ExampleObject(
                            name = "Null password",
                            value = "{\"error\": \"Password must not be null\"}",
                            summary = "Triggered when the password field is null"
                    )
            }))
    })
    public ResponseEntity<?> save(@Valid @RequestBody UserRequest requestUser, BindingResult result) {
        if (result.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            for (FieldError error : result.getFieldErrors()) {
                errors.put("error", error.getDefaultMessage());
            }
            return ResponseEntity.badRequest().body(errors);
        }

        User userSaved = userService.save(requestUser);

        UserDTO userDTO = userMapper.userToUserDTO(userSaved);

        return ResponseEntity.status(HttpStatus.CREATED).body(userDTO);
    }

    @GetMapping("{id}")
    @Operation(summary = "Find a user by id", description = "Retrieve detailed profile information for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User profile found successfully",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "404", description = "Not found user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object", example = "{\"message\": \"Not found a user {id}\"}")))
    })
    public UserDTO getUserById(@PathVariable long id) {
        UserDTO userDTO = userService.getById(id);

        return userDTO.phone() != null && !userDTO.isHiddenPhone() ? userDTO.getByPhone(aesEncryptionService.decrypt(userDTO.phone())) : userDTO;
    }

    @GetMapping("/playlists/{userId}")
    @Operation(summary = "Get user playlists", description = "Retrieve all playlists belonging to a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user playlists",
                    content = @Content(schema = @Schema(implementation = PlaylistDTO.class))),
            @ApiResponse(responseCode = "404", description = "Not found user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object", example = "{\"error\": \"Not found a user\"}")))
    })
    public List<PlaylistDTO> getPlaylists(@PathVariable long userId) {
        return userService.getPlaylists(userId);
    }

    @DeleteMapping(value = "/playlist/delete/{playlistId}/{songId}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Remove a song from the playlist", description = "Delete a specific song reference inside a user's playlist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Song successfully removed from playlist",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Song from playlist deleted successfully\"}"))),
            @ApiResponse(responseCode = "404", description = "Not found playlist or song",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"), examples = {
                    @ExampleObject(
                            name = "Not found playlist",
                            value = "{\"message\": \"Playlist not found with {id}\"}",
                            summary = "Triggered when the specified playlistId does not exist"
                    ),
                    @ExampleObject(
                            name = "Not found user",
                            value = "{\"message\": \"User not found with {id}\"}",
                            summary = "Triggered when the user associated with the request does not exist"
                    ),
                    @ExampleObject(
                            name = "Not found song",
                            value = "{\"message\": \"Song not found with {id}\"}",
                            summary = "Triggered when the specified songId does not exist inside the playlist"
                    )
            }))
    })
    public ResponseEntity<MessageResponse> deleteSongFromPlaylist(@PathVariable long playlistId, @PathVariable long songId) {
        userService.deleteSongFromPlaylist(playlistId, songId);

        return ResponseEntity.ok(new MessageResponse("Song from playlist deleted successfully"));
    }

    @PutMapping("/block/{id}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Block a user", description = "Suspend a user account to prevent them from logging into the application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User blocked successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"User blocked successfully\"}"))),
            @ApiResponse(responseCode = "404", description = "Not found user",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"User not found with {id}\"}")))
    })
    public ResponseEntity<MessageResponse> block(@PathVariable long id) {
        userService.blockUser(id);

        return ResponseEntity.ok(new MessageResponse("User blocked successfully"));
    }

    @GetMapping(value = "/picture/{id}", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE})
    @Operation(summary = "Get user profile avatar", description = "Fetch and render the binary image file of a user's avatar picture")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avatar image retrieved successfully",
            content = {
                    @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "blob", format = "binary")),
                    @Content(mediaType = MediaType.IMAGE_JPEG_VALUE, schema = @Schema(type = "blob", format = "binary"))
            }),
            @ApiResponse(responseCode = "404", description = "Not found user",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(type = "object", example = "{\"error\": \"User not found with {id}\"}")))
    })
    public ResponseEntity<Resource> getUserPicture(@PathVariable long id, WebRequest webRequest) throws IOException {
        UserDTO userDTO = getUserById(id);

        if (userDTO.provider().equals(Provider.GOOGLE)) {
            Resource resource = userService.getProfilePicture(id);

            long lastModified = resource.lastModified();

            if (webRequest.checkNotModified(lastModified)) {
                return null;
            }

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache().sMaxAge(1, TimeUnit.DAYS))
                    .lastModified(lastModified)
                    .contentType(MediaType.parseMediaType(userDTO.contentType()))
                    .body(resource);
        } else {
            Path path = Paths.get(PICTURE_PATH).resolve(userDTO.profilePicture()).normalize();

            Resource resource = new UrlResource(path.toUri());

            long lastModified = resource.lastModified();

            if (webRequest.checkNotModified(lastModified)) {
                return null;
            }

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache().sMaxAge(1, TimeUnit.DAYS))
                    .lastModified(lastModified)
                    .contentType(MediaType.parseMediaType(userDTO.contentType()))
                    .body(resource);
        }
    }

    @PatchMapping("/update/{id}")
    @Operation(summary = "Update user profile details", description = "Modify specific fields of user metadata (name, email, password, phone, description)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"User updated successfully\"}"))),
            @ApiResponse(responseCode = "404", description = "Not found user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object", example = "{\"error\": \"Not found a user\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation failed for profile update",
            content = @Content(schema = @Schema(type = "object"), examples = {
                    @ExampleObject(
                            name = "Invalid email format",
                            value = "{\"error\": \"Write your email\"}",
                            summary = "Triggered when the provided email string is invalid or empty"
                    ),
                    @ExampleObject(
                            name = "Invalid phone format",
                            value = "{\"error\": \"Write your phone number\"}",
                            summary = "Triggered when the phone number format does not comply with system patterns"
                    )
            }))
    })
    @SecurityRequirement(name = "Playlist")
    public ResponseEntity<?> updateUser(@PathVariable long id, @RequestBody @Valid UserUpdateRequest userUpdate, BindingResult result) throws Exception {
        if (result.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            for (FieldError error : result.getFieldErrors()) {
                errors.put("error", error.getDefaultMessage());
            }
            return ResponseEntity.badRequest().body(errors);
        }

        userService.updateUser(userUpdate, id);

        return ResponseEntity.ok(new MessageResponse("User updated successfully"));
    }

    @PutMapping("/change_role/{userId}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Change user account role", description = "Allows an administrator to modify the authority level/role of a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account role updated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Changed role successfully.\"}"))),
            @ApiResponse(responseCode = "404", description = "Not found user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"User not found with {id}\"}")))
    })
    public ResponseEntity<MessageResponse> changeRoleUser(@RequestBody Role role, @PathVariable long userId) {
        userService.changeRole(role, userId);

        return ResponseEntity.ok(new MessageResponse("Changed role to " + role + " successfully"));
    }

    @PostMapping(value = "/upload-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Upload profile avatar image", description = "Upload a JPEG or PNG image to serve as the user's profile icon")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avatar uploaded successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Avatar uploaded successfully\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid image file uploaded",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"), examples = {
                    @ExampleObject(
                            name = "Unsupported media type",
                            value = "{\"message\": \"Invalid image or png format\"}",
                            summary = "Triggered when the uploaded file is not a JPEG or PNG format"
                    ),
                    @ExampleObject(
                            name = "Missing file",
                            value = "{\"message\": \"File must not be null or empty\"}",
                            summary = "Triggered when the avatar multipart file is missing or has zero bytes"
                    )
            }))
    })
    public ResponseEntity<MessageResponse> uploadAvatar(@RequestPart MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("File must not be null or empty"));
        } else if (!file.getContentType().equals("image/jpeg") && !file.getContentType().equals("image/png")) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid image jpg or png format"));
        }

        userService.uploadAvatar(file);

        return ResponseEntity.ok(new MessageResponse("Avatar uploaded successfully"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Get current authenticated user session", description = "Retrieve profile details for the currently logged-in user session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session profile retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "401", description = "User is not authenticated")
    })
    public ResponseEntity<UserDTO> getCurrentUser() {
        User currentUser = userService.getCurrentUser();

        UserDTO userDTO = userMapper.userToUserDTO(currentUser);

        return ResponseEntity.ok(userDTO);
    }

    @PostMapping("/subscribe/{id}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Subscribe to a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully subscribed to a user",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"User subscribed successfully\"}"))),
            @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"User not found\"}"))),
            @ApiResponse(responseCode = "403", description = "User subscribed to user",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"You're already subscribed!\"}")))
    })
    public ResponseEntity<MessageResponse> subscribeToUser(@PathVariable long id) {
        subscriptionService.subscribeToUser(id);

        return ResponseEntity.ok(new MessageResponse("User subscribed successfully"));
    }

    @PostMapping("/unsubscribe/{id}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Unsubscribe from a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully unsubscribed from user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"User unsubscribed successfully\"}"))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"User not found\"}"))),
            @ApiResponse(responseCode = "403", description = "User unsubscribed from user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"You're not subscribed!\"}")))
    })
    public ResponseEntity<MessageResponse> unsubscribeFromUser(@PathVariable long id) {
        subscriptionService.unsubscribeFromUser(id);

        return ResponseEntity.ok(new MessageResponse("User unsubscribed successfully"));
    }

    @GetMapping("/subscriptions/{userId}")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Get user subscriptions", description = "Returns a list of subscriptions for the specified user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriptions retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, array =  @ArraySchema(schema = @Schema(implementation = SubscriptionDTO.class))))
    })
    public ResponseEntity<List<SubscriptionDTO>> getSubscriptions(@PathVariable long userId) {
        return ResponseEntity.ok(userService.getSubscriptions(userId));
    }
}
