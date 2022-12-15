import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

@JsonPropertyOrder("sentences", "label")
data class OriginalCommentData(val sentences: String, val label: Int)

data class Classification(val displayName: String)
data class PreparedData(val textContent: String, val classificationAnnotation: Classification)

fun main() {
    val originalInput = File("./DataPrep/src/main/resources/ISEP Sexist Data Labeling.csv")
    val outputFile = File("./DataPrep/src/main/resources/training_data.jsonl")
    val jsonMapper = jacksonObjectMapper()
    val csvMapper = CsvMapper().registerKotlinModule() as CsvMapper

    val ingestSchema = csvMapper.schemaFor(OriginalCommentData::class.java)
    val reader = csvMapper.readerFor(OriginalCommentData::class.java)
        .with(ingestSchema)
        .readValues<OriginalCommentData>(originalInput)

    if (!outputFile.exists()) outputFile.createNewFile()
    outputFile.writer().use { writer ->
        for (comment in reader) {
            val jsonified = PreparedData(
                textContent = comment.sentences,
                classificationAnnotation = Classification(if (comment.label == 1) "sexist" else "non-sexist")
            )

            writer.write(jsonMapper.writeValueAsString(jsonified) + "\n")
        }
    }
}