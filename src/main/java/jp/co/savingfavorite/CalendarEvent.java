package jp.co.savingfavorite;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class CalendarEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    private Favorite favorite;
    private String title;
    private String kind;
    private LocalDate startDate;
    private LocalDate endDate;
    private int reminderDays;
    @Column(length = 1000)
    private String notes;
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User value) { user = value; }
    public Favorite getFavorite() { return favorite; }
    public void setFavorite(Favorite value) { favorite = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate value) { startDate = value; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate value) { endDate = value; }
    public int getReminderDays() { return reminderDays; }
    public void setReminderDays(int value) { reminderDays = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
}
