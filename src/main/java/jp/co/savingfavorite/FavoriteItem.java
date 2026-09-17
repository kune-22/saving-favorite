package jp.co.savingfavorite;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 推しに紐づくグッズ情報。 */
@Entity
@Table(name = "favorite_items")
public class FavoriteItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String category;
    private BigDecimal price;
    private Integer quantity;
    private LocalDate purchasedDate;
    /** メンバーシップに入会した日（定期支払いの基準日）。 */
    private LocalDate membershipJoinedDate;
    @jakarta.persistence.Lob
    @jakarta.persistence.Column(columnDefinition = "CLOB")
    private String imageUrl;
    private String status = "OWNED";
    private String recurrence = "NONE";
    private LocalDate deadline;
    @jakarta.persistence.Column(length = 2048)
    private String storeUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "favorite_id", nullable = false)
    private Favorite favorite;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected FavoriteItem() {
    }

    public FavoriteItem(String name, String category, BigDecimal price, Integer quantity,
                        LocalDate purchasedDate, String imageUrl) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.quantity = quantity;
        this.purchasedDate = purchasedDate;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecurrence() { return recurrence; }
    public void setRecurrence(String recurrence) { this.recurrence = recurrence; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public String getStoreUrl() { return storeUrl; }
    public void setStoreUrl(String storeUrl) { this.storeUrl = storeUrl; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public LocalDate getPurchasedDate() { return purchasedDate; }
    public void setPurchasedDate(LocalDate purchasedDate) { this.purchasedDate = purchasedDate; }
    public LocalDate getMembershipJoinedDate() { return membershipJoinedDate; }
    public void setMembershipJoinedDate(LocalDate membershipJoinedDate) { this.membershipJoinedDate = membershipJoinedDate; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Favorite getFavorite() { return favorite; }
    public void setFavorite(Favorite favorite) { this.favorite = favorite; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
