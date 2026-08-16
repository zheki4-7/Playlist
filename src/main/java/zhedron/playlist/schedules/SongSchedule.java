package zhedron.playlist.schedules;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import zhedron.playlist.entity.Song;
import zhedron.playlist.enums.Status;
import zhedron.playlist.repository.SongRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SongSchedule {

    private final SongRepository songRepository;

    public SongSchedule(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @Scheduled(fixedDelay = 60000)
    public void schedule() {
        List<Song> songsScheduled = songRepository.findAllByStatus(Status.SCHEDULED);

        if (!songsScheduled.isEmpty()) {
            for (Song song : songsScheduled) {
                System.out.println(song.getPublishedAt().isBefore(LocalDateTime.now()));
                if (song.getPublishedAt().isBefore(LocalDateTime.now())) {
                    song.setStatus(Status.PUBLISHED);
                    songRepository.save(song);
                }
            }
        }
    }
}
