package jp.co.savingfavorite;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteItemRepository extends JpaRepository<FavoriteItem, Long> {
    List<FavoriteItem> findAllByFavoriteIdOrderByIdAsc(Long favoriteId);
}
