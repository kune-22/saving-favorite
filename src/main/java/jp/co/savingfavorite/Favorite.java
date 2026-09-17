package jp.co.savingfavorite;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "favorites")
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    @jakarta.persistence.Lob
    @jakarta.persistence.Column(columnDefinition = "CLOB")
    private String imageUrl;
    private java.math.BigDecimal monthlyBudget;
    private Integer imageSize = 56;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "favorite", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FavoriteItem> items = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Favorite() {
    }

    public Favorite(String name, String description, String imageUrl) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public java.math.BigDecimal getMonthlyBudget() { return monthlyBudget; }
    public void setMonthlyBudget(java.math.BigDecimal value) { monthlyBudget = value; }
    public Integer getImageSize() { return imageSize; }
    public void setImageSize(Integer value) { imageSize = value; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public List<FavoriteItem> getItems() { return items; }

    public void addItem(FavoriteItem item) {
        items.add(item);
        item.setFavorite(this);
    }

    public void removeItem(FavoriteItem item) {
        items.remove(item);
        item.setFavorite(null);
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
