### Deep Research Agent with Koog Framework

This repository contains a Deep Research Agent built using the Koog framework. The agent is designed to perform in-depth research tasks by leveraging advanced AI capabilities.
To test it out, you can simply run ```Main.kt``` and adjust the prompt there as required.

The custom strategy can be found in the Strategies package. THe strategy is designed so that it first creates a plan, and then searches the web according to the section
that is going to be written, and then writes the section. This is repeated until the entire plan is written. This ensures that each section can get specific attention instead
of writing the entire report at once.

I used Google's custom search API for web searching to reduce costs, but you can replace it with any search tool of your choice such as BrightData, SerpAPI, etc. 

#### Requirements
Place the following in a .env file in the project root:
OPENAI_API_KEY - Your OpenAI API key.
GOOGLE_SEARCH_API_KEY - Your Google Custom Search API key.
GOOGLE_SEARCH_ENGINE_ID - Your Google Custom Search Engine ID.


