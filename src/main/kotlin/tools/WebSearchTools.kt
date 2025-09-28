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
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class SearchResult(
    val title: String,
    @SerialName("link")
    val url: String,
    val snippet: String
)


@LLMDescription("Tools for performing web searches and retrieving relevant information.")
class WebSearchTools : ToolSet {

    @Tool
    @LLMDescription("Performs a web search using the provided query and returns the URLs of relevant results." +
            "Returns the top 10 responses from Google Custom Search API.")
    fun search(
        @LLMDescription("The search query.")
        query: String
    ): String {
        val client = HttpClient(CIO)

        val url = URLBuilder("https://customsearch.googleapis.com/customsearch/v1").apply{
            parameters.append("key", dotenv()["GOOGLE_SEARCH_API_KEY"])
            parameters.append("cx", dotenv()["SEARCH_ENGINE_ID"])
            parameters.append("q", query)
            parameters.append("num","10")
        }.build()

       val response = runBlocking { client.get(url) }
       client.close()
        return response.toString()
    }

    @Tool
    @LLMDescription("Scrapes the content of the specified webpage URL and returns the text content.")
    fun scrape(
        @LLMDescription("The URL of the webpage to scrape.")
        url: String
    ): String {
        val client = HttpClient(CIO)

        val html = runBlocking { client.get(url).body<String>() }
        val doc: Document = Ksoup.parse(html = html)

        val body = doc.body().text()

        client.close()
        return body
    }

}