package ua.khpi.websockets.todo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ua.khpi.websockets.IntegrationTest;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests share one database, so each test creates its own lists and never relies on totals. */
@IntegrationTest
class TodoControllerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void ownerManagesListAndItems() throws Exception {
		long listId = createList("alice", "Groceries", Visibility.PRIVATE);

		long itemId = id(mvc.perform(json(post("/api/lists/{id}/items", listId), "{\"title\": \" Milk \"}")
				.with(as("alice")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Milk"))
			.andExpect(jsonPath("$.done").value(false))
			.andReturn().getResponse().getContentAsString());

		mvc.perform(json(patch("/api/items/{id}", itemId), "{\"done\": true}").with(as("alice")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.done").value(true))
			.andExpect(jsonPath("$.title").value("Milk"));

		mvc.perform(json(patch("/api/lists/{id}", listId), "{\"title\": \"Shopping\"}").with(as("alice")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Shopping"))
			.andExpect(jsonPath("$.items[0].done").value(true));

		mvc.perform(delete("/api/items/{id}", itemId).with(as("alice"))).andExpect(status().isNoContent());
		mvc.perform(delete("/api/lists/{id}", listId).with(as("alice"))).andExpect(status().isNoContent());
		mvc.perform(get("/api/lists").with(as("alice")))
			.andExpect(jsonPath("$[*].id", not(hasItem((int) listId))));
	}

	@Test
	void privateListIsInvisibleToOthers() throws Exception {
		long listId = createList("alice", "Secret plans", Visibility.PRIVATE);
		long itemId = id(mvc.perform(json(post("/api/lists/{id}/items", listId), "{\"title\": \"Surprise\"}")
				.with(as("alice")))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString());

		mvc.perform(get("/api/lists").with(as("bob")))
			.andExpect(jsonPath("$[*].id", not(hasItem((int) listId))));
		mvc.perform(json(post("/api/lists/{id}/items", listId), "{\"title\": \"Peek\"}").with(as("bob")))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/lists/{id}", listId).with(as("bob"))).andExpect(status().isNotFound());

		// items are checked separately (by item id), so test that path too
		mvc.perform(json(patch("/api/items/{id}", itemId), "{\"done\": true}").with(as("bob")))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/items/{id}", itemId).with(as("bob"))).andExpect(status().isNotFound());
		mvc.perform(get("/api/lists").with(as("alice")))
			.andExpect(jsonPath("$[?(@.id == %d)].items[0].done".formatted(listId)).value(false));
	}

	@Test
	void publicListIsSharedButOnlyOwnerCanRenameOrDeleteIt() throws Exception {
		long listId = createList("alice", "Team tasks", Visibility.PUBLIC);

		mvc.perform(get("/api/lists").with(as("bob")))
			.andExpect(jsonPath("$[?(@.id == %d)].owner".formatted(listId)).value("alice"));
		mvc.perform(json(post("/api/lists/{id}/items", listId), "{\"title\": \"Bob was here\"}").with(as("bob")))
			.andExpect(status().isCreated());
		mvc.perform(json(patch("/api/lists/{id}", listId), "{\"title\": \"Mine now\"}").with(as("bob")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Only the owner can change this list"));
		mvc.perform(delete("/api/lists/{id}", listId).with(as("bob"))).andExpect(status().isForbidden());
	}

	@Test
	void replaceMyListsSwapsOnlyMyOwnLists() throws Exception {
		// carol is used only here and in the WebSocket replace test, so other tests' lists aren't affected
		long oldPublic = createList("carol", "Old public", Visibility.PUBLIC);
		long oldPrivate = createList("carol", "Old private", Visibility.PRIVATE);
		long alicesList = createList("alice", "Alice keeps this", Visibility.PUBLIC);

		mvc.perform(json(put("/api/lists/mine"), """
				[
				  {"title": "New A", "visibility": "PUBLIC",
				   "items": [{"title": "one", "done": false}, {"title": "two", "done": true}]},
				  {"title": "New B", "visibility": "PRIVATE", "items": []}
				]""").with(as("carol")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].title").value("New A"))
			.andExpect(jsonPath("$[0].items.length()").value(2))
			.andExpect(jsonPath("$[0].items[1].done").value(true))
			.andExpect(jsonPath("$[1].visibility").value("PRIVATE"));

		mvc.perform(get("/api/lists").with(as("carol")))
			.andExpect(jsonPath("$[*].id", not(hasItem((int) oldPublic))))
			.andExpect(jsonPath("$[*].id", not(hasItem((int) oldPrivate))))
			.andExpect(jsonPath("$[*].id", hasItem((int) alicesList)))
			.andExpect(jsonPath("$[?(@.owner == 'carol')].title", containsInAnyOrder("New A", "New B")));
	}

	@Test
	void replaceMyListsValidatesEveryNestedItem() throws Exception {
		mvc.perform(json(put("/api/lists/mine"), """
				[{"title": "Ok", "visibility": "PUBLIC", "items": [{"title": "  ", "done": false}]}]""")
				.with(as("carol")))
			.andExpect(status().isBadRequest());
		mvc.perform(json(put("/api/lists/mine"), """
				[{"title": "No items field", "visibility": "PUBLIC"}]""").with(as("carol")))
			.andExpect(status().isBadRequest());
		mvc.perform(json(put("/api/lists/mine"), "[null]").with(as("carol"))).andExpect(status().isBadRequest());
		mvc.perform(json(put("/api/lists/mine"), """
				[{"title": "Null item", "visibility": "PUBLIC", "items": [null]}]""").with(as("carol")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void invalidInputIsRejected() throws Exception {
		mvc.perform(json(post("/api/lists"), "{\"title\": \"  \", \"visibility\": \"PUBLIC\"}").with(as("alice")))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/lists"), "{\"title\": \"No visibility\"}").with(as("alice")))
			.andExpect(status().isBadRequest());

		long listId = createList("alice", "Validation", Visibility.PRIVATE);
		long itemId = id(mvc.perform(json(post("/api/lists/{id}/items", listId), "{\"title\": \"Item\"}")
				.with(as("alice")))
			.andReturn().getResponse().getContentAsString());
		mvc.perform(json(patch("/api/items/{id}", itemId), "{\"title\": \"   \"}").with(as("alice")))
			.andExpect(status().isBadRequest());
	}

	private long createList(String user, String title, Visibility visibility) throws Exception {
		String body = mvc.perform(json(post("/api/lists"),
				"{\"title\": \"%s\", \"visibility\": \"%s\"}".formatted(title, visibility))
				.with(as(user)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.owner").value(user))
			.andReturn().getResponse().getContentAsString();
		return id(body);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	/** A signed-in user without going through /login (covered by AuthControllerTest). */
	private static RequestPostProcessor as(String username) {
		return jwt().jwt(token -> token.subject(username));
	}

	private static long id(String json) {
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

}
