package org.jbtasks.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import com.fleeksoft.ksoup.Ksoup
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GoogleCustomSearchResponseItem(
    val title: String,
    @SerialName("link")
    val url: String,
    val snippet: String
)

@Serializable
data class GoogleSearchResponse(
    val kind: String,
    val url: Map<String, String> = emptyMap(),
    val items: List<GoogleCustomSearchResponseItem> = emptyList()
)

object WebSearchTool : Tool<WebSearchTool.Args, ToolResult.Text>() {

    override val argsSerializer: KSerializer<Args> = Args.serializer()
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "web-search",
        description = "Performs a web search using the provided query" +
                " and returns the body text of the first 10 results."
    )
    @Serializable
    data class Args(
        val query: String
    ) : ToolArgs

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val dotEnv = dotenv()
    private val googleSearchKey = dotEnv["GOOGLE_SEARCH_API_KEY"]
    private val searchEngineId = dotEnv["SEARCH_ENGINE_ID"]

    public override suspend fun execute(args: Args): ToolResult.Text {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val sourceFilter = "(site:wikipedia.org OR site:medium.com OR site:nytimes.com " +
                "OR site:bbc.com OR site:theguardian.com" +
                " OR site:smithsonianmag.com OR site:nationalgeographic.com" +
                " OR site:britannica.com OR site:history.com OR site:.edu) -filetype:pdf"
        val finalQuery = "${args.query} $sourceFilter"

        val searchUrl = URLBuilder("https://customsearch.googleapis.com/customsearch/v1").apply {
            parameters.append("key", googleSearchKey)
            parameters.append("cx", searchEngineId)
            parameters.append("q", finalQuery)
            parameters.append("num", "1")
        }.build()

        val searchResponse = client.get(searchUrl).body<GoogleSearchResponse>()

        val scrapedContent = coroutineScope {
            searchResponse.items.map { item ->
                async {
                    try {
                        val html = client.get(item.url).body<String>()
                        val doc = Ksoup.parse(html)
                        val contentElements = doc.select("p")
                        "Title: ${item.title}\nSnippet: ${item.snippet}\nContent:\n${contentElements.text()}\n\n"
                    } catch (e: Exception) {
                        "Could not scrape ${item.url}: ${e.message}\n\n"
                    }
                }
            }.map { it.await() }
        }

        client.close()

        return ToolResult.Text(scrapedContent.joinToString(""))
    }
}