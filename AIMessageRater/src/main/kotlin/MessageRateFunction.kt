import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.aiplatform.util.ValueConverter
import com.google.cloud.aiplatform.v1.EndpointName
import com.google.cloud.aiplatform.v1.PredictionServiceClient
import com.google.cloud.aiplatform.v1.PredictionServiceSettings
import com.google.cloud.aiplatform.v1.Value
import com.google.cloud.aiplatform.v1.schema.predict.instance.TextClassificationPredictionInstance
import com.google.cloud.aiplatform.v1.schema.predict.prediction.ClassificationPredictionResult
import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import mu.KotlinLogging
import tech.fearless.purpledatahacks.ClassificationMessage
import tech.fearless.purpledatahacks.PotentiallySexistMessage
import java.io.IOException

class MessageRateFunction : HttpFunction {
    private val logger = KotlinLogging.logger {}

    @Throws(IOException::class)
    override fun service(request: HttpRequest, response: HttpResponse) {
        val jsonMapper = jacksonObjectMapper()
        val messagesToClassify = jsonMapper.readValue<List<ClassificationMessage>>(request.reader)

        logger.info { "Received ${messagesToClassify.size} slack messages to classify." }

        val predictionServiceSettings = PredictionServiceSettings.newBuilder()
            .setEndpoint("us-central1-aiplatform.googleapis.com:443")
            .build()
        val classifiedMessages = mutableListOf<PotentiallySexistMessage>()

        PredictionServiceClient.create(predictionServiceSettings).use { aiClient ->
            val project = "hacking-hate-speech"
            val location = "us-central1"
            val endpointId = "FILL ME IN LATER"

            val endpointName = EndpointName.of(project, location, endpointId)
            val batchSize = 20
            for ((windowIdx, messageBatch) in messagesToClassify.asSequence().windowed(batchSize).withIndex()) {
                val classificationValues = messageBatch.map { message ->
                    val classifyInstance = TextClassificationPredictionInstance.newBuilder()
                        .setContent(message.message)
                        .build()
                    ValueConverter.toValue(classifyInstance)
                }

                logger.info {
                    val windowStart = windowIdx * batchSize
                    val windowEnd = ((windowIdx + 1) * batchSize).coerceAtMost(messagesToClassify.size)
                    "Classifying messages $windowStart-$windowEnd..."
                }

                val predictionResults = aiClient.predict(endpointName, classificationValues, ValueConverter.EMPTY_VALUE)
                val convertedPredictionResults = predictionResults.predictionsList.asSequence()
                    .map { predictionValue ->
                        ValueConverter.fromValue(ClassificationPredictionResult.newBuilder(), predictionValue) as ClassificationPredictionResult
                    }.withIndex()
                    .filter { (_, result) -> result.getDisplayNames(0) == "sexist" }
                    .map { (messageIdx, result) ->
                        val sourceMessage = messageBatch[messageIdx]
                        PotentiallySexistMessage(sourceMessage.sendingUser, sourceMessage.message, result.getConfidences(0))
                    }.toList()

                logger.info { "Found ${convertedPredictionResults.size} potentially sexist messages in that batch." }
                classifiedMessages.addAll(convertedPredictionResults)
            }
        }

        logger.info { "In total, there were ${classifiedMessages.size} sexist messages found." }

        jsonMapper.writeValue(response.writer, classifiedMessages)
    }
}
