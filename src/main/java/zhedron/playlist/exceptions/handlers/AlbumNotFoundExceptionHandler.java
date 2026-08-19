package zhedron.playlist.exceptions.handlers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zhedron.playlist.exceptions.AlbumNotFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class AlbumNotFoundExceptionHandler {
    @ExceptionHandler(AlbumNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAlbumNotFoundException(AlbumNotFoundException e) {
        Map<String, String> response = new HashMap<>();

        response.put("message", e.getMessage());

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
}
