package zhedron.playlist.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zhedron.playlist.dto.request.LoginRequest;
import zhedron.playlist.dto.response.MessageResponse;
import zhedron.playlist.dto.response.TokenResponse;
import zhedron.playlist.entity.RefreshToken;
import zhedron.playlist.entity.User;
import zhedron.playlist.exceptions.RefreshTokenNotFoundException;
import zhedron.playlist.exceptions.UserNotFoundException;
import zhedron.playlist.services.JwtService;
import zhedron.playlist.services.RefreshTokenService;
import zhedron.playlist.services.UserService;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


@RestController
@Tag(name = "Authorization API", description = "Authorization with login, password and get JWT token")
public class AuthController {
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(JwtService jwtService, AuthenticationManager authenticationManager, UserService userService, RefreshTokenService refreshTokenService) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Authenticate user", description = "Log in with email and password to receive JWT access and refresh tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully authenticated and generated tokens",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = """
                    {
                      "accessToken": "{accessToken}",
                      "refreshToken": "{refreshToken}"
                    }"""))),
            @ApiResponse(responseCode = "401", description = "Authentication failed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"), examples = {
                    @ExampleObject(
                            name = "Invalid credentials",
                            value = "{\"message\": \"Invalid email or password\"}",
                            summary = "Triggered when the email does not exist or the password is incorrect"
                    ),
                    @ExampleObject(
                            name = "Account is blocked",
                            value = "{\"message\": \"Your account is locked\"}",
                            summary = "Triggered when the user account has been suspended or blocked"
                    )
            }))
    })
    public ResponseEntity<?> login (@Valid @RequestBody LoginRequest loginRequest, BindingResult bindingResult, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            for (FieldError error : bindingResult.getFieldErrors()) {
                errors.put("error", error.getDefaultMessage());
            }
            return ResponseEntity.badRequest().body(errors);
        }

        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(), loginRequest.getPassword()
            ));

            if (authentication.isAuthenticated()) {
                User userFound = userService.findByEmail(loginRequest.getEmail());

                if (userFound != null) {
                    String accessToken = jwtService.generateToken(userFound);

                    RefreshToken refreshToken = refreshTokenService.generateRefreshToken(userFound.getEmail());

                    ResponseCookie cookie = ResponseCookie.from("accessToken", accessToken)
                            .httpOnly(true)
                            .path("/")
                            .maxAge(Duration.ofMinutes(15))
                            .build();

                    ResponseCookie cookie1 = ResponseCookie.from("refreshToken", refreshToken.getRefreshToken())
                            .httpOnly(true)
                            .path("/refreshtoken")
                            .maxAge(Duration.ofDays(30))
                            .build();

                    TokenResponse tokenResponse = new TokenResponse(accessToken, refreshToken.getRefreshToken());

                    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .header(HttpHeaders.SET_COOKIE, cookie1.toString()).body(tokenResponse);
                }
            }
        } catch (BadCredentialsException | UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Invalid email or password"));
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Your account is locked"));
        }

        return null;
    }

    @PostMapping("/refreshtoken")
    @Operation(summary = "Refresh authentication tokens", description = "Exchange a valid refresh token for a new access and refresh token pair to extend the session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tokens successfully refreshed",
            content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not found refresh token",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", example = "{\"message\": \"Refresh Token not found with {refreshToken}\"}")))
    })
    public ResponseEntity<?> refreshToken (HttpServletRequest request) {
        String token = null;

        Cookie[] cookies = request.getCookies();
        
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("refreshToken")) {
                    token = cookie.getValue();
                    System.out.println("Docker test");
                }
            }
        }

        if (token != null) {
            return refreshTokenService.findByRefreshToken(token)
                    .map(refreshTokenService::verifyRefreshToken)
                    .map(RefreshToken::getUser)
                    .map(user -> {
                        String accessToken = jwtService.generateToken(user);

                        User userFound = userService.findByEmail(user.getEmail());

                        RefreshToken refreshToken = refreshTokenService.generateRefreshToken(userFound.getEmail());

                        TokenResponse tokenResponse = new TokenResponse(accessToken, refreshToken.getRefreshToken());

                        ResponseCookie cookie_access = ResponseCookie.from("accessToken", accessToken)
                                .httpOnly(true)
                                .path("/")
                                .maxAge(Duration.ofMinutes(15))
                                .build();

                        ResponseCookie cookie_refresh = ResponseCookie.from("refreshToken", refreshToken.getRefreshToken())
                                .httpOnly(true)
                                .path("/refreshtoken")
                                .maxAge(Duration.ofDays(30))
                                .build();

                        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie_access.toString())
                                .header(HttpHeaders.SET_COOKIE, cookie_refresh.toString()).body(tokenResponse);
                    })
                    .orElseThrow(() -> new RefreshTokenNotFoundException("Refresh Token not found"));
        }

        return ResponseEntity.notFound().build();
    }

    @PostMapping("/auth/logout")
    @SecurityRequirement(name = "Playlist")
    @Operation(summary = "Log out user", description = "Clear authentication cookies and terminate the current user session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully logged out"),
            @ApiResponse(responseCode = "401", description = "User is not logged in")
    })
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie_accessToken = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();

        ResponseCookie cookie_refreshToken = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/refreshtoken")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie_accessToken.toString())
                .header(HttpHeaders.SET_COOKIE, cookie_refreshToken.toString())
                .build();
    }
}
