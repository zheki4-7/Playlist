package zhedron.playlist.dto;


public record SubscriptionDTO(long id, long userId, String name,
                              String profilePicture, String contentType) {
}
