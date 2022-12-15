import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import com.slack.api.Slack
import com.slack.api.methods.MethodsClient
import mu.KotlinLogging
import tech.fearless.purpledatahacks.ClassificationMessage
import java.io.IOException

data class ChannelRequest(val channel: String)

class MessagesExtractorFunction : HttpFunction {
    private val logger = KotlinLogging.logger {}

    private fun fetchUserData(userId: String, slackClient: MethodsClient, memoryMap: MutableMap<String, String>): String {
        return memoryMap.getOrPut(userId) {
            val foundUser = slackClient.usersInfo { req ->
                req.user(userId)
            }

            if (!foundUser.isOk) {
                throw IllegalArgumentException("Could not find user: $userId")
            }

            "${foundUser.user.realName} (${foundUser.user.profile.displayName})"
        }
    }

    @Throws(IOException::class)
    override fun service(request: HttpRequest, response: HttpResponse) {
        val jsonParser = jacksonObjectMapper()
        val parsedRequest = jsonParser.readValue<ChannelRequest>(request.reader)
        val slackClient = Slack.getInstance().methods(System.getenv("SLACK_BOT_TOKEN"))


        // Attempt to join channel if not already a member
        val targetChannelInfo = slackClient.conversationsInfo { req ->
            req.channel(parsedRequest.channel)
        }
        if (!targetChannelInfo.isOk) {
            response.setStatusCode(404)
            response.writer.write("Could not find channel ${parsedRequest.channel}")
            return
        }
        logger.info { "Checking on channel ${targetChannelInfo.channel.name}..." }
        if (!targetChannelInfo.channel.isMember) {
            val joinResponse = slackClient.conversationsJoin { req ->
                req.channel(parsedRequest.channel)
            }
            if (!joinResponse.isOk) {
                response.setStatusCode(403)
                response.writer.write("Failed to join channel ${parsedRequest.channel}, it may be private")
                return
            }
        }

        // Read messages
        val userIdToName = mutableMapOf<String, String>()

        var historyPage = slackClient.conversationsHistory { req ->
            req.channel(parsedRequest.channel)
                .limit(20)
        }
        val foundMessages = historyPage.messages
            .filter { it.subtype.isNullOrEmpty() }
            .map {
                val user = fetchUserData(it.user, slackClient, userIdToName)
                ClassificationMessage(user, it.text)
            }
            .toMutableList()

        while (historyPage.isHasMore) {
            historyPage = slackClient.conversationsHistory { req ->
                req.channel(parsedRequest.channel)
                    .cursor(historyPage.responseMetadata.nextCursor)
                    .limit(20)
            }

            foundMessages.addAll(historyPage.messages
                .filter { it.subtype.isNullOrEmpty() }
                .map {
                    val user = fetchUserData(it.user, slackClient, userIdToName)
                    ClassificationMessage(user, it.text)
                }
            )
        }

        logger.info { "Passing along ${foundMessages.size} messages..." }
        jsonParser.writeValue(response.writer, foundMessages)
    }
}