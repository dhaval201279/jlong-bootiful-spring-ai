package com.its.AiAssistant;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;

import org.springaicommunity.mcp.security.client.sync.AuthenticationMcpTransportContextProvider;
import org.springaicommunity.mcp.security.client.sync.oauth2.http.client.OAuth2AuthorizationCodeSyncHttpRequestCustomizer;

@SpringBootApplication
@Slf4j
public class AiAssistantApplication {
	@Autowired
	Environment env;

	public static void main(String[] args) {
		//log.info("datasource url : {} ",  System.getProperty("spring.datasource.url"));
		//log.info("user timezone : {} ",  System.getProperty("user.timezone"));



		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

		SpringApplication.run(AiAssistantApplication.class, args);
	}

	/**
	 * Create a PromptChatMemoryAdvisor bean backed by a JDBC-based chat memory repository.
	 * The advisor uses a MessageWindowChatMemory that persists messages to the provided
	 * DataSource so chat history can be reused by the ChatClient.
	 *
	 * @param dataSource the JDBC DataSource used to persist chat memory
	 * @return configured PromptChatMemoryAdvisor
	 */
	@Bean
	PromptChatMemoryAdvisor promptChatMemoryAdvisor (DataSource dataSource) {
		var jdbc = JdbcChatMemoryRepository
				.builder()
				.dataSource(dataSource)
				.build();

		var mwa = MessageWindowChatMemory
				.builder()
				.chatMemoryRepository(jdbc)
				.build();

		return PromptChatMemoryAdvisor
				.builder(mwa)
				.build();
	}

	@Bean
	QuestionAnswerAdvisor questionAnswerAdvisor (VectorStore vectorStore) {
		return QuestionAnswerAdvisor.builder(vectorStore).build();
	}

@Configuration
class SecurityConfiguration {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity security) {
		return security
				.authorizeHttpRequests(authorize ->
						authorize.anyRequest().permitAll())
				.oauth2Client(Customizer.withDefaults())
				.build();
	}

		@Bean
		OAuth2AuthorizationCodeSyncHttpRequestCustomizer auth2AuthorizationCodeSyncHttpRequestCustomizer(OAuth2AuthorizedClientManager authorizedClientManager) {
			return new OAuth2AuthorizationCodeSyncHttpRequestCustomizer(authorizedClientManager,
					"authserver");
		}

		@Bean
		McpSyncClientCustomizer mcpSyncClientCustomizer() {
			return (_, spec) -> spec
					.transportContextProvider(new AuthenticationMcpTransportContextProvider());
		}

	}

	@PostConstruct
	public void checkKey() {

		String key = env.getProperty("spring.ai.openai.api-key");
		//log.info("Resolved OpenAI key starts with: {}", key != null ? key.substring(0, 7) : "null");
		/*System.out.println("Resolved OpenAI key prefix: " +
				env.getProperty("spring.ai.openai.api-key").substring(0, 10) + "...");*/
	}

}

interface DogRepository extends ListCrudRepository<Dog, Integer> {

}

record Dog(@Id int id,String name,String description) {}

@Controller
@ResponseBody
class AssistantController {
	private final ChatClient ai;

	AssistantController(//DogAdoptionScheduler dogAdoptionScheduler,
						ToolCallbackProvider scheduler,
						DogRepository dogRepository,
						VectorStore vectorStore,
						QuestionAnswerAdvisor qaa,
						PromptChatMemoryAdvisor promptChatMemoryAdvisor,
						ChatClient.Builder ai) {

		if (false) {
			dogRepository.findAll()
					.forEach(dog -> {
						var dogument = new Document("id: %s, name : %s, description: %s".formatted(dog.id(), dog.name(), dog.description()));
						vectorStore.add(List.of(dogument));
					});
		}

		var system = """
                
                You are an AI powered assistant to help people adopt a dog from the adoptions agency named Pooch Palace with locations in Antwerp, Seoul, Tokyo, Singapore, Paris, Mumbai, New Delhi, Barcelona, San Francisco, and London. Information about the dogs availables will be presented below. If there is no information, then return a polite response suggesting wes don't have any dogs available.
                
                If somebody asks for a time to pick up the dog, don't ask other questions: simply provide a time by consulting the tools you have available.
                
                """;

		this.ai = ai
				//.defaultTools(dogAdoptionScheduler)
				.defaultToolCallbacks(scheduler)
				.defaultAdvisors(qaa, promptChatMemoryAdvisor)
				.defaultSystem(system)
				.build();
	}

	@GetMapping("/{user}/ask")
	String ask(@PathVariable String user,
	//DogAdoptionSuggestion ask(@PathVariable String user,
			   @RequestParam String question) {
		/**
		 * prompt - creates a request object that will hold one or more messages (roles: system/user/assistant), model options (temperature, model id), and any call-specific metadata
		 * user - Add a message with the user role whose content is the question string. This becomes the user input the model will respond to.
		 * advisors -
		 * 		The configured PromptChatMemoryAdvisor (the default advisor) reads that parameter to locate the correct conversation in the ChatMemory (here backed by MessageWindowChatMemory + JDBC repository).
		 * 		The advisor typically injects prior messages from that conversation into the outgoing prompt (as historical messages or a system/context block) so the model sees conversation history.
	 * 			After the model responds, the advisor may also persist the new user and assistant messages back into chat memory.
		 * 		Multiple advisors can be applied; each can mutate the request before send or handle post-send actions.
		 * 	call - Execute the request synchronously (blocking)
		 * 	content - extract the assistant text content from the response wrapper
		 * 	entity - extracts out fields from the full content string which is multiline and returns that object as json formatted response
		 */
		return this
				.ai
				.prompt()
				.user(question)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, user))
				.call()
				.content();
				//.entity(DogAdoptionSuggestion.class);
	}
}

/*@Service
@Slf4j
class DogAdoptionScheduler {
	@McpTool(description = "Schedule an appointment to pick up or adopt a dog from Pooch Palace location")
	String schedule(@McpToolParam int dogId) {
		var i = Instant
				.now()
				.plus(3, ChronoUnit.DAYS)
				.toString();
		log.info("Scheduling dogId : {} for i : {} ", dogId, i);
		return i;
	}
}*/
record DogAdoptionSuggestion (int dogId, String name) {}
