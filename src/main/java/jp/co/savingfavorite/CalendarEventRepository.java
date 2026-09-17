package jp.co.savingfavorite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    List<CalendarEvent> findAllByUserIdOrderByStartDateAsc(Long userId);
}
