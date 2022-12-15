import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import com.slack.api.Slack
import com.slack.api.methods.kotlin_extension.request.chat.blocks
import tech.fearless.purpledatahacks.PotentiallySexistMessage
import java.io.IOException
import kotlin.jvm.Throws

class MonitorDigestFunction : HttpFunction {
    @Throws(IOException::class)
    override fun service(request: HttpRequest, response: HttpResponse) {
        val jsonMapper = jacksonObjectMapper()
        // This should come from my function
        val problematicMessages = jsonMapper.readValue<List<PotentiallySexistMessage>>(request.reader)
        val slackClient = Slack.getInstance().methods(System.getenv("SLACK_BOT_TOKEN"))

        // Just something to get you started
        slackClient.chatPostMessage { req ->
            req.channel("blah") // Might be something different for DMs? Might have to reference the slack api docs
                .blocks {
                    section {
                        plainText("Hello world!")
                    }
                }
        }
    }
}