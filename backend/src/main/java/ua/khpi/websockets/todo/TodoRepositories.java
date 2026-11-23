package ua.khpi.websockets.todo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TodoListRepository extends JpaRepository<TodoList, Long> {

	/** Public lists plus the user's own private ones, with items and owner loaded in one query. */
	@EntityGraph(attributePaths = { "items", "owner" })
	@Query("""
			select l from TodoList l
			where l.visibility = ua.khpi.websockets.todo.Visibility.PUBLIC or l.owner.username = :username
			order by l.id
			""")
	List<TodoList> findVisibleTo(String username);

	/** One list with items and owner, for re-reading it after an event. */
	@EntityGraph(attributePaths = { "items", "owner" })
	Optional<TodoList> findWithItemsById(Long id);

	/** Several lists with items and owner in one query. */
	@EntityGraph(attributePaths = { "items", "owner" })
	List<TodoList> findWithItemsByIdInOrderById(Collection<Long> ids);

	/** A user's own lists (items stay lazy, they are not loaded). */
	List<TodoList> findByOwnerUsername(String username);

	/** Bulk delete; the database removes the items (ON DELETE CASCADE) without loading them. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from TodoList l where l.owner.id = :ownerId")
	int deleteAllOwnedBy(Long ownerId);

}

interface TodoItemRepository extends JpaRepository<TodoItem, Long> {
}
