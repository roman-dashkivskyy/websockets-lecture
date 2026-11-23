package ua.khpi.websockets.todo;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

}

interface TodoItemRepository extends JpaRepository<TodoItem, Long> {
}
