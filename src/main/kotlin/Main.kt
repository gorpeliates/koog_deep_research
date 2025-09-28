package org.jbtasks

import kotlinx.coroutines.runBlocking
import org.jbtasks.tools.WebSearchTools

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {

    val response = runBlocking {
        WebSearchTools().search("aritifical intelligence").first()
    }
    val md = runBlocking { WebSearchTools().scrape(response.url) }
    println(md)
}
