import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.aiplatform.util.ValueConverter
import com.google.cloud.aiplatform.v1.EndpointName
import com.google.cloud.aiplatform.v1.PredictionServiceClient
import com.google.cloud.aiplatform.v1.PredictionServiceSettings
import com.google.cloud.aiplatform.v1.schema.predict.instance.TextClassificationPredictionInstance
import com.google.cloud.aiplatform.v1.schema.predict.prediction.ClassificationPredictionResult
import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import mu.KotlinLogging
import tech.fearless.purpledatahacks.ClassificationMessage
import tech.fearless.purpledatahacks.PotentiallySexistMessage
import java.io.IOException

data class MessageRateInput(val sexistConfidenceThreshold: Float, val messagesToClassify: List<ClassificationMessage>)

class MessageRateFunction : HttpFunction {
    private val logger = KotlinLogging.logger {}

    @Throws(IOException::class)
    override fun service(request: HttpRequest, response: HttpResponse) {
        val jsonMapper = jacksonObjectMapper()
        val (confidenceThreshold, messagesToClassify) = jsonMapper.readValue<MessageRateInput>(request.reader)

        logger.info { "Received ${messagesToClassify.size} slack messages to classify." }

        val predictionServiceSettings = PredictionServiceSettings.newBuilder()
            .setEndpoint("us-central1-aiplatform.googleapis.com:443")
            .build()
        val classifiedMessages = mutableListOf<PotentiallySexistMessage>()

        PredictionServiceClient.create(predictionServiceSettings).use { aiClient ->

            // This is the name of the project where the Vertex AI endpoint lives
            val project = System.getenv("ML_PROJECT")
                ?: throw Exception("ML_PROJECT not provided! Cannot communicate with Vertex AI.")
            // This is the region where the Vertex AI endpoint is deployed in GCloud
            val location = System.getenv("ML_ENDPOINT_REGION") ?: "us-central1"
            // This is the ID of the exposed endpoint from Vertex AI we should communicate with
            val endpointId = System.getenv("ML_ENDPOINT_ID")
                ?: throw Exception("ML_ENDPOINT_ID not provided! Cannot communicate with Vertex AI.")

            val endpointName = EndpointName.of(project, location, endpointId)
            for ((idx, message) in messagesToClassify.asSequence().withIndex()) {
                val classifyInstance = TextClassificationPredictionInstance.newBuilder()
                    .setContent(message.message)
                    .build()
                val classificationValue = listOf(ValueConverter.toValue(classifyInstance))

                logger.info {
                    "Classifying message #${idx + 1}..."
                }

                val predictionResults = aiClient.predict(endpointName, classificationValue, ValueConverter.EMPTY_VALUE)
                val convertedPredictionResult = ValueConverter.fromValue(
                    ClassificationPredictionResult.newBuilder(), predictionResults.predictionsList.first()) as ClassificationPredictionResult
                val sexistConfidence = convertedPredictionResult.displayNamesList
                    .zip(convertedPredictionResult.confidencesList)
                    .filter { (label, _) -> label == "sexist" }
                    .map { (_, confidence) -> confidence }
                    .first()

                if (sexistConfidence > confidenceThreshold) {
                    logger.info {
                        val percentage = sexistConfidence * 100
                        val percentageString = "%.1f%%".format(percentage)
                        "Sexist message detected with $percentageString confidence"
                    }
                    classifiedMessages.add(PotentiallySexistMessage(message.sendingUser, message.message, sexistConfidence))
                }
            }
        }

        logger.info { "In total, there were ${classifiedMessages.size} potentially sexist messages found." }

        jsonMapper.writeValue(response.writer, classifiedMessages)
    }
}
