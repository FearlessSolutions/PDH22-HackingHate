import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import com.slack.api.Slack
import com.slack.api.methods.kotlin_extension.request.chat.blocks
import com.slack.api.model.kotlin_extension.block.element.ButtonStyle
import tech.fearless.purpledatahacks.PotentiallySexistMessage
import java.io.IOException
import kotlin.jvm.Throws

data class FunctionInput(val monitorUser:String, val messages:List<PotentiallySexistMessage>)
class MonitorDigestFunction : HttpFunction {
    @Throws(IOException::class)
    override fun service(request: HttpRequest, response: HttpResponse) {
        val jsonMapper = jacksonObjectMapper()
        // This should come from my function
        val problematicMessages = jsonMapper.readValue<FunctionInput>(request.reader)
        val slackClient = Slack.getInstance().methods(System.getenv("SLACK_BOT_TOKEN"))
        for (message in problematicMessages.messages) {
            val percentConfidence = "%.1f%%".format(message.confidence * 100)

            val postMessageResponse = slackClient.chatPostMessage { req ->
                req.channel(problematicMessages.monitorUser) // Might be something different for DMs? Might have to reference the slack api docs
                    .text("Potential sexist speech detected!")
                    .blocks {
                        header {
                            text("Is this message sexist?")
                        }
                        section {
                            plainText("I'm $percentConfidence confident this is sexist.")
                        }
                        section {
                            markdownText( "> *${message.message}*")
                        }
                        actions {
                            button {
                                text("Neutral")
                                style(ButtonStyle.PRIMARY)
                            }
                            button {
                                text("Sexist")
                                style(ButtonStyle.DANGER)
                            }
                        }
                        divider()
                    }
            }

            if (!postMessageResponse.isOk) {
                println(postMessageResponse.error)
            }
        }
    }
}