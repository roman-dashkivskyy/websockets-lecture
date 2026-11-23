package ua.khpi.websockets.todo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import ua.khpi.websockets.auth.AppUser;

@Entity
@Table(name = "todo_list")
public class TodoList {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Visibility visibility;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id")
	private AppUser owner;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<TodoItem> items = new ArrayList<>();

	protected TodoList() {
	}

	public TodoList(String title, Visibility visibility, AppUser owner) {
		this.title = title;
		this.visibility = visibility;
		this.owner = owner;
	}

	/** Adds the item to this list's collection too, so a snapshot taken right after includes it. */
	public TodoItem addItem(String title) {
		TodoItem item = new TodoItem(this, title);
		items.add(item);
		return item;
	}

	public void removeItem(TodoItem item) {
		items.remove(item);
	}

	public boolean isOwnedBy(String username) {
		return owner.getUsername().equals(username);
	}

	public boolean isVisibleTo(String username) {
		return visibility == Visibility.PUBLIC || isOwnedBy(username);
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Visibility getVisibility() {
		return visibility;
	}

	public AppUser getOwner() {
		return owner;
	}

	public List<TodoItem> getItems() {
		return items;
	}

}
