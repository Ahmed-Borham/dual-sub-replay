package com.kienhoang.dualsubreplay.assistant

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AiAnthropicMessagesTest {
    private val tool = AiTool("set_speed", "Changes the playback speed.", """{"type":"object","properties":{"speed":{"type":"number"}}}""")

    @Test
    fun theSystemPromptGoesApartAndEveryRequestHasALimit() {
        val body =
            JSONObject(
                anthropicRequestBody(
                    "claude-haiku-5-5",
                    listOf(AiWireMessage(AiRole.SYSTEM, "guide"), AiWireMessage(AiRole.USER, "What is に?")),
                ),
            )
        assertEquals("claude-haiku-5-5", body.getString("model"))
        assertEquals("guide", body.getString("system"))
        assertEquals(ANTHROPIC_MAX_TOKENS, body.getInt("max_tokens"))
        val messages = body.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        val content = messages.getJSONObject(0).getJSONArray("content")
        assertEquals(1, content.length())
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("What is に?", content.getJSONObject(0).getString("text"))
        assertEquals(setOf("model", "max_tokens", "system", "messages"), body.keys().asSequence().toSet())
    }

    @Test
    fun theThinkingLevelIsSentAsEffortAndActionsWithTheirSchema() {
        val body = JSONObject(anthropicRequestBody("claude-haiku-5-5", listOf(AiWireMessage(AiRole.USER, "Faster")), "high", listOf(tool)))
        assertEquals("high", body.getJSONObject("output_config").getString("effort"))
        val sent = body.getJSONArray("tools").getJSONObject(0)
        assertEquals("set_speed", sent.getString("name"))
        assertEquals(
            "number",
            sent
                .getJSONObject("input_schema")
                .getJSONObject("properties")
                .getJSONObject("speed")
                .getString("type"),
        )
        assertFalse(sent.has("function"))
    }

    @Test
    fun questionsInARowBecomeOneTurnAndEmptyAnswersAreLeftOut() {
        val body =
            JSONObject(
                anthropicRequestBody(
                    "claude-haiku-5-5",
                    listOf(
                        AiWireMessage(AiRole.SYSTEM, "guide"),
                        AiWireMessage(AiRole.USER, "First"),
                        AiWireMessage(AiRole.ASSISTANT, ""),
                        AiWireMessage(AiRole.USER, "Second"),
                    ),
                ),
            )
        val messages = body.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals(2, messages.getJSONObject(0).getJSONArray("content").length())
    }

    @Test
    fun picturesAndPdfsGoBeforeTheQuestionAsBase64Blocks() {
        val files =
            listOf(
                AiAttachment("a.jpg", AiAttachmentKind.PICTURE, "data:image/jpeg;base64,AAAA"),
                AiAttachment("b.pdf", AiAttachmentKind.PDF, "data:application/pdf;base64,BBBB"),
            )
        val content =
            JSONObject(anthropicRequestBody("claude-haiku-5-5", listOf(AiWireMessage(AiRole.USER, "What is this?", files))))
                .getJSONArray("messages")
                .getJSONObject(0)
                .getJSONArray("content")
        assertEquals(3, content.length())
        val picture = content.getJSONObject(0)
        assertEquals("image", picture.getString("type"))
        assertEquals("image/jpeg", picture.getJSONObject("source").getString("media_type"))
        assertEquals("AAAA", picture.getJSONObject("source").getString("data"))
        assertEquals("document", content.getJSONObject(1).getString("type"))
        assertEquals("application/pdf", content.getJSONObject(1).getJSONObject("source").getString("media_type"))
        assertEquals("text", content.getJSONObject(2).getString("type"))
    }

    @Test
    fun anAnswerWithActionsGoesBackUnchangedWithAllResultsInOneTurn() {
        val replyBody =
            """
            {"model":"claude-haiku-5-5","stop_reason":"tool_use","content":[
              {"type":"thinking","thinking":"","signature":"sig"},
              {"type":"text","text":"Speeding up."},
              {"type":"tool_use","id":"toolu_1","name":"set_speed","input":{"speed":1.5}},
              {"type":"tool_use","id":"toolu_2","name":"set_speed","input":{}}
            ]}
            """.trimIndent()
        val reply = parseAnthropicReply(replyBody)
        assertEquals("Speeding up.", reply.text)
        assertEquals(listOf("toolu_1", "toolu_2"), reply.toolCalls.map { it.id })
        assertEquals("""{"speed":1.5}""", reply.toolCalls[0].arguments)
        assertEquals(1.5, aiToolArguments(reply.toolCalls[0])!!.getDouble("speed"), 0.0)
        assertEquals("{}", reply.toolCalls[1].arguments)
        assertEquals("claude-haiku-5-5", reply.model)
        val messages =
            JSONObject(
                anthropicRequestBody(
                    "claude-haiku-5-5",
                    listOf(
                        AiWireMessage(AiRole.SYSTEM, "guide"),
                        AiWireMessage(AiRole.USER, "Faster"),
                        AiWireMessage(AiRole.ASSISTANT, reply.text, toolCalls = reply.toolCalls, rawContent = reply.rawContent),
                        AiWireMessage(AiRole.TOOL, "Done: speed 1.5.", toolCallId = "toolu_1"),
                        AiWireMessage(AiRole.TOOL, "Not done: no speed.", toolCallId = "toolu_2"),
                    ),
                    tools = listOf(tool),
                ),
            ).getJSONArray("messages")
        assertEquals(listOf("user", "assistant", "user"), (0 until messages.length()).map { messages.getJSONObject(it).getString("role") })
        // The thinking block and its signature go back exactly as they came.
        assertEquals(JSONObject(replyBody).getJSONArray("content").toString(), messages.getJSONObject(1).getJSONArray("content").toString())
        val results = messages.getJSONObject(2).getJSONArray("content")
        assertEquals(2, results.length())
        assertEquals("tool_result", results.getJSONObject(0).getString("type"))
        assertEquals("toolu_1", results.getJSONObject(0).getString("tool_use_id"))
        assertEquals("Not done: no speed.", results.getJSONObject(1).getString("content"))
    }

    @Test
    fun aPlainAnswerKeepsNoRawContent() {
        val reply = parseAnthropicReply("""{"content":[{"type":"thinking","thinking":""},{"type":"text","text":" Hello "}]}""")
        assertEquals("Hello", reply.text)
        assertNull(reply.rawContent)
    }

    @Test
    fun aRefusalOrAnEmptyReplyIsABadReply() {
        for (body in listOf("""{"content":[],"stop_reason":"refusal"}""", """{"content":[]}""", "<html>")) {
            try {
                parseAnthropicReply(body)
                fail(body)
            } catch (error: AiChatException) {
                assertEquals(body, AiErrorKind.BAD_REPLY, error.kind)
            }
        }
        try {
            parseAnthropicReply("""{"content":[],"stop_reason":"refusal"}""")
        } catch (error: AiChatException) {
            assertEquals("Claude declined to answer this question.", error.detail)
        }
    }

    @Test
    fun anthropicErrorsMapToTheirOwnMessages() {
        val lowCredit = "Your credit balance is too low to access the Anthropic API."
        val message = providerErrorMessage("""{"type":"error","error":{"type":"invalid_request_error","message":"$lowCredit"}}""")
        assertEquals(lowCredit, message)
        assertEquals(AiErrorKind.NO_CREDIT, aiErrorKindForStatus(400, message))
        assertEquals(AiErrorKind.SERVER, aiErrorKindForStatus(529, "Overloaded"))
        assertEquals(
            AiErrorKind.UNSUPPORTED_THINKING,
            aiErrorKindForStatus(400, "This model does not support the effort parameter.", sentThinking = true),
        )
    }

    @Test
    fun claudeRequestsUseTheMessagesAddressAndAnthropicsKeyHeader() {
        val sent = mutableListOf<Request>()
        val transport = AiHttpTransport(answering(sent, """{"content":[{"type":"text","text":"OK"}]}"""))
        val reply =
            runBlocking {
                transport.complete(
                    AiChatRequest(
                        AiProvider.CLAUDE.baseUrl,
                        "claude-haiku-5-5",
                        "sk-ant-key",
                        listOf(AiWireMessage(AiRole.USER, "OK?")),
                        format = AiWireFormat.ANTHROPIC_MESSAGES,
                    ),
                )
            }
        assertEquals("OK", reply.text)
        val request = sent.single()
        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("sk-ant-key", request.header("x-api-key"))
        assertEquals(ANTHROPIC_API_VERSION, request.header("anthropic-version"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun claudeModelsAreListedInOnePage() {
        val sent = mutableListOf<Request>()
        val transport =
            AiHttpTransport(answering(sent, """{"data":[{"type":"model","id":"claude-sonnet-5-5"},{"id":"claude-haiku-5-5"}]}"""))
        val models = runBlocking { transport.listModels(AiProvider.CLAUDE.baseUrl, "sk-ant-key", AiWireFormat.ANTHROPIC_MESSAGES) }
        assertEquals(listOf("claude-haiku-5-5", "claude-sonnet-5-5"), models.map { it.id })
        assertEquals("https://api.anthropic.com/v1/models?limit=1000", sent.single().url.toString())
        assertEquals("sk-ant-key", sent.single().header("x-api-key"))
    }

    @Test
    fun otherServicesKeepTheBearerKey() {
        val sent = mutableListOf<Request>()
        val transport = AiHttpTransport(answering(sent, """{"choices":[{"message":{"content":"OK"}}]}"""))
        runBlocking {
            transport.complete(
                AiChatRequest(AiProvider.OPENAI.baseUrl, "gpt-5-mini", "sk-proj-key", listOf(AiWireMessage(AiRole.USER, "OK?"))),
            )
        }
        assertEquals("https://api.openai.com/v1/chat/completions", sent.single().url.toString())
        assertEquals("Bearer sk-proj-key", sent.single().header("Authorization"))
        assertNull(sent.single().header("x-api-key"))
    }

    /** A client that records each request and answers it with [body], without any network. */
    private fun answering(
        sent: MutableList<Request>,
        body: String,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                sent += chain.request()
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
}
