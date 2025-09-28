package org.jbtasks.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
data class GoogleCustomSearchResponseItems(
    val title: String,
    @SerialName("link")
    val url: String,
    val snippet: String
)

@Serializable
data class GoogleSearchResponse(
    val kind: String,
    val url: Map<String, String> = emptyMap(),
    val items: List<GoogleCustomSearchResponseItems> = emptyList()
)

@LLMDescription("Tools for performing web searches and retrieving relevant information.")
class WebSearchTools : ToolSet {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val dotEnv = dotenv()
    private val brightDataKey = dotEnv["BRIGHTDATA_API_KEY"]
    private val googleSearchKey = dotEnv["GOOGLE_SEARCH_API_KEY"]
    private val searchEngineId = dotEnv["SEARCH_ENGINE_ID"]

    @Tool
    @LLMDescription("Performs a web search using the provided query and returns the URLs of relevant results." +
            "Returns the top 10 responses from Google Custom Search API.")
    suspend fun search(
        @LLMDescription("The search query.")
        query: String
    ): List<GoogleCustomSearchResponseItems> {

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        val url = URLBuilder("https://customsearch.googleapis.com/customsearch/v1").apply{
            parameters.append("key", googleSearchKey)
            parameters.append("cx", searchEngineId)
            parameters.append("q", query)
            parameters.append("num","10")
        }.build()

       val response =  client.get(url).body<GoogleSearchResponse>().items

        client.close()
        return response

    }

    @Tool
    @LLMDescription("Scrapes the content of the specified webpage URL and returns the text content.")
    suspend fun scrape(
        @LLMDescription("The URL of the webpage to scrape.")
        url: String
    ): String {

        val client = HttpClient(CIO)

        val html =  client.get(url).body<String>()
        val doc: Document = Ksoup.parse(html = html)

        val body = doc.body().text()

        client.close()
        return body

    }

}